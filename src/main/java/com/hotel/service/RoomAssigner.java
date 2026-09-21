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

    public RoomAssigner(RoomRepository roomRepository) {
        this(roomRepository, new TagQuotaPolicy());
    }

    public RoomAssigner(RoomRepository roomRepository, TagQuotaPolicy tagQuotaPolicy) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.tagQuotaPolicy = (tagQuotaPolicy != null) ? tagQuotaPolicy : new TagQuotaPolicy();
        this.tagScoringEngine = new TagScoringEngine();
    }

    public Optional<Room> assign(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation은 필수입니다.");

        if (reservation.isAssigned()) {
            return roomRepository.findByRoomNumber(reservation.getAssignedRoomNumber());
        }

        StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
        TagPreference tagPref = reservation.getTagPreference();

        // Pass 1: Hard Filter (공실 & 룸타입 일치 검증)
        List<Room> candidates = roomRepository.findAll().stream()
                .filter(room -> room.isAvailable(targetPeriod))
                .filter(room -> room.getRoomType() == reservation.getBookedRoomType())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Pass 1-1: 태그 킵(Safety Quota) 방어 필터
        List<Room> allocatableCandidates = candidates.stream()
                .filter(room -> isRoomAllocatableUnderQuota(room, candidates, tagPref))
                .toList();

        if (allocatableCandidates.isEmpty()) {
            return Optional.empty();
        }

        // Pass 2: Soft Scoring
        GuestPreference pref = reservation.getPreference();
        int stayNights = reservation.getStayNights();

        Optional<Room> bestRoomOpt = allocatableCandidates.stream()
                .max(Comparator.comparingInt(room -> calculateScore(room, pref, tagPref, stayNights)));

        bestRoomOpt.ifPresent(bestRoom -> {
            bestRoom.bookPeriod(targetPeriod);
            reservation.assignRoom(bestRoom.getRoomNumber());
        });

        return bestRoomOpt;
    }

    /**
     * [오류 3 수정] 특정 태그를 명시적으로 요청한 고객은 해당 태그의 쿼터 예외를 받고,
     * 고객이 요구하지 않은 다른 태그의 쿼터 임계치가 걸려있다면 방어를 유지함
     */
    private boolean isRoomAllocatableUnderQuota(Room room, List<Room> sameTypeAvailableRooms, TagPreference tagPref) {
        for (String roomTag : room.getTags()) {
            int holdQuota = tagQuotaPolicy.getHoldQuota(roomTag);
            if (holdQuota <= 0) continue;

            long remainingTagRooms = sameTypeAvailableRooms.stream()
                    .filter(r -> r.hasTag(roomTag))
                    .count();

            if (remainingTagRooms <= holdQuota) {
                // 이 태그를 고객이 명시적으로 요청하지 않았다면 킵 객실 보호를 위해 배정 차단
                if (tagPref == null || !tagPref.preferredTags().contains(roomTag)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * [오류 1 수정] 점수 이중 합산 및 승수 중복 증폭 제거:
     * TagPreference가 활성화되어 있다면 TagScoringEngine 중심 채점,
     * 비어있다면 레거시 GuestPreference 중심 채점으로 단일화
     */
    public int calculateScore(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        int totalScore = 0;

        if (tagPref != null && !tagPref.isEmpty()) {
            // 태그 엔진 채점 (연박 가중치 1.3배 포함)
            totalScore += tagScoringEngine.calculateScore(room, tagPref, stayNights);
        } else {
            // 레거시 룰 채점 (태그 미사용 시 호환용)
            totalScore += calculateScore(room, pref, stayNights);
        }

        // 보수적 층수 안배 가감점 적용
        totalScore += calculateConservativeFloorAdjustment(room, pref, tagPref, stayNights);

        return totalScore;
    }

    private int calculateConservativeFloorAdjustment(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        boolean requestedHighFloor = (pref != null && pref.getFloorPref() == FloorPref.HIGH)
                || (tagPref != null && (
                tagPref.preferredTags().contains(RoomTag.HIGH_FLOOR.code())
                        || tagPref.preferredTags().contains(RoomTag.VIEW_TOKYO_TOWER.code())
        ));

        // 고층 요청이 없고 5박 미만 단기 투숙객
        if (!requestedHighFloor && stayNights < 5) {
            if (room.getFloor() >= 10) {
                return -25; // 상층부 룸 보존 페널티
            }
            if (stayNights <= 2 && room.getFloor() <= 6) {
                return 15;  // 저층 집적 선소진 보너스
            }
        }

        return 0;
    }

    public int calculateScore(Room room, GuestPreference pref, int stayNights) {
        int baseScore = 0;

        if (pref != null) {
            if (pref.getFloorPref() == FloorPref.HIGH) {
                baseScore += (room.getFloor() >= 10) ? 15 : -10;
            } else if (pref.getFloorPref() == FloorPref.LOW) {
                baseScore += (room.getFloor() <= 6) ? 15 : -10;
            }

            if (pref.getElevatorPref() == ElevatorPref.NEAR) {
                baseScore += room.isNearElevator() ? 20 : -10;
            } else if (pref.getElevatorPref() == ElevatorPref.AWAY) {
                baseScore += !room.isNearElevator() ? 20 : -15;
            }

            if (pref.getCornerPref() == CornerPref.PREFER) {
                baseScore += room.isCorner() ? 10 : 0;
            } else if (pref.getCornerPref() == CornerPref.AVOID) {
                baseScore += !room.isCorner() ? 10 : -5;
            }

            if (pref.isPreferQuiet()) {
                if (!room.isNearElevator()) baseScore += 15;
                if (room.isCorner()) baseScore += 10;
            }
        }

        if (stayNights >= 3) {
            baseScore = (int) (baseScore * 1.3);
            if (!room.isNearElevator()) baseScore += 10;
            if (room.isCorner()) baseScore += 5;
        }

        return baseScore;
    }

    public TagQuotaPolicy getTagQuotaPolicy() {
        return tagQuotaPolicy;
    }
}