package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.StayPeriod;
import com.hotel.domain.TagPreference;
import com.hotel.domain.TagQuotaPolicy;
import com.hotel.repository.RoomRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RoomAssigner {

    private final RoomRepository roomRepository;
    private final TagScoringEngine tagScoringEngine;
    private final TagQuotaPolicy tagQuotaPolicy;

    // 기본 생성자 (기본 쿼터 정책 적용)
    public RoomAssigner(RoomRepository roomRepository) {
        this(roomRepository, new TagQuotaPolicy());
    }

    // 쿼터 정책 주입용 생성자
    public RoomAssigner(RoomRepository roomRepository, TagQuotaPolicy tagQuotaPolicy) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.tagQuotaPolicy = (tagQuotaPolicy != null) ? tagQuotaPolicy : new TagQuotaPolicy();
        this.tagScoringEngine = new TagScoringEngine();
    }

    public Optional<Room> assign(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation은 필수입니다.");

        // 이미 배정된 경우 기존 객실 반환
        if (reservation.isAssigned()) {
            return roomRepository.findByRoomNumber(reservation.getAssignedRoomNumber());
        }

        StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
        TagPreference tagPref = reservation.getTagPreference();

        // Pass 1: Hard Filter (요청 기간 공실 검증 및 계약 룸타입 검증)
        List<Room> candidates = roomRepository.findAll().stream()
                .filter(room -> room.isAvailable(targetPeriod))
                .filter(room -> room.getRoomType() == reservation.getBookedRoomType())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Pass 1-1: 태그 킵(Hold Quota) 방어 필터 적용
        // 해당 태그를 명시적으로 요청하지 않은 일반 손님에게는 킵 임계치(남은 공실 <= 킵 수량)에 도달한 방 배정 제한
        List<Room> allocatableCandidates = candidates.stream()
                .filter(room -> isRoomAllocatableUnderQuota(room, candidates, tagPref))
                .toList();

        // 쿼터 방어로 남은 방이 없으면 일반 예약에는 보존 객실을 내주지 않고 만실(배정 보류) 처리
        if (allocatableCandidates.isEmpty()) {
            return Optional.empty();
        }

        // Pass 2: Soft Scoring (고객 선호도 + 태그 스위치 점수 + 보수적 층수 안배 + 연박 가중치)
        GuestPreference pref = reservation.getPreference();
        int stayNights = reservation.getStayNights();

        Optional<Room> bestRoomOpt = allocatableCandidates.stream()
                .max(Comparator.comparingInt(room -> calculateScore(room, pref, tagPref, stayNights)));

        // 배정 확정: 기간 등록 및 예약 객실 번호 동기화
        bestRoomOpt.ifPresent(bestRoom -> {
            bestRoom.bookPeriod(targetPeriod);
            reservation.assignRoom(bestRoom.getRoomNumber());
        });

        return bestRoomOpt;
    }

    /**
     * [태그 킵(Quota) 방어 검증 로직]
     * 특정 객실이 보유한 태그의 남은 공실 수가 킵(Quota) 수량 이하일 경우:
     * - 고객이 해당 태그를 직접 선호(preferredTags)한 경우에만 배정 허용
     * - 해당 태그를 요구하지 않은 일반 고객에게는 배정 금지 (킵 유지)
     */
    private boolean isRoomAllocatableUnderQuota(Room room, List<Room> sameTypeAvailableRooms, TagPreference tagPref) {
        for (String roomTag : room.getTags()) {
            int holdQuota = tagQuotaPolicy.getHoldQuota(roomTag);
            if (holdQuota <= 0) continue;

            // 동일 룸타입 공실 중 이 태그를 가진 남은 방 수 카운트
            long remainingTagRooms = sameTypeAvailableRooms.stream()
                    .filter(r -> r.hasTag(roomTag))
                    .count();

            // 남은 방이 킵 설정 수량 이하로 떨어졌을 때
            if (remainingTagRooms <= holdQuota) {
                // 고객이 이 태그를 명시적으로 원하지 않았다면 배정 불가 (방을 아껴둠)
                if (tagPref == null || !tagPref.preferredTags().contains(roomTag)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 룰 기반 점수 + 동적 태그 스위칭 매칭 점수 + 실무 보수적 층수 배분 통합 계산
     */
    public int calculateScore(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        int baseRuleScore = calculateScore(room, pref, stayNights);
        int tagScore = tagScoringEngine.calculateScore(room, tagPref, stayNights);
        int conservatismAdjustment = calculateConservativeFloorAdjustment(room, pref, tagPref, stayNights);

        return baseRuleScore + tagScore + conservatismAdjustment;
    }

    /**
     * [실무 보수적 배정 규칙]
     * 1. 고층/전망 요청이 없는 5박 미만 단기 투숙객이 10층 이상 상층부를 볼 때 -> 로얄층 보존을 위해 감점 (-25점)
     * 2. 특별 요청이 없는 1~2박 단기 투숙객이 3~6층 저층을 볼 때 -> 저층 선소진 및 층별 집적을 위해 가산 (+15점)
     */
    private int calculateConservativeFloorAdjustment(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        boolean requestedHighFloor = (pref != null && pref.getFloorPref() == FloorPref.HIGH)
                || (tagPref != null && (
                tagPref.preferredTags().contains(RoomTag.HIGH_FLOOR.code())
                        || tagPref.preferredTags().contains(RoomTag.VIEW_TOKYO_TOWER.code())
        ));

        // 고층 요청이 없고 5박 미만인 경우
        if (!requestedHighFloor && stayNights < 5) {
            // 상층부(10층 이상) 객실은 장기 VIP 및 현장 업셀을 위해 아껴둠
            if (room.getFloor() >= 10) {
                return -25;
            }
            // 1~2박 단기 고객은 3~6층 저층부터 채워 청소 동선 효율화
            if (stayNights <= 2 && room.getFloor() <= 6) {
                return 15;
            }
        }

        return 0;
    }

    /**
     * 기존 호환용 점수 계산 메서드 (단위 테스트 및 레거시 유지)
     */
    public int calculateScore(Room room, GuestPreference pref, int stayNights) {
        int baseScore = 0;

        if (pref != null) {
            // 1. 층수 선호도
            if (pref.getFloorPref() == FloorPref.HIGH) {
                baseScore += (room.getFloor() >= 10) ? 15 : -10;
            } else if (pref.getFloorPref() == FloorPref.LOW) {
                baseScore += (room.getFloor() <= 6) ? 15 : -10;
            }

            // 2. 엘리베이터 선호도 (NEAR / AWAY)
            if (pref.getElevatorPref() == ElevatorPref.NEAR) {
                baseScore += room.isNearElevator() ? 20 : -10;
            } else if (pref.getElevatorPref() == ElevatorPref.AWAY) {
                baseScore += !room.isNearElevator() ? 20 : -15;
            }

            // 3. 코너룸 선호도 (PREFER / AVOID)
            if (pref.getCornerPref() == CornerPref.PREFER) {
                baseScore += room.isCorner() ? 10 : 0;
            } else if (pref.getCornerPref() == CornerPref.AVOID) {
                baseScore += !room.isCorner() ? 10 : -5;
            }

            // 4. 조용한 방 복합 채점
            if (pref.isPreferQuiet()) {
                if (!room.isNearElevator()) {
                    baseScore += 15;
                }
                if (room.isCorner()) {
                    baseScore += 10;
                }
            }
        }

        // 5. 연박 가중치 적용 (3박 이상 우대)
        if (stayNights >= 3) {
            baseScore = (int) (baseScore * 1.3);

            if (!room.isNearElevator()) {
                baseScore += 10;
            }
            if (room.isCorner()) {
                baseScore += 5;
            }
        }

        return baseScore;
    }

    public TagQuotaPolicy getTagQuotaPolicy() {
        return tagQuotaPolicy;
    }
}