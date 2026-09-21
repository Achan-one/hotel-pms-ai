package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.dto.AssignmentAlert;

import java.time.LocalDate;
import java.util.*;

public class RoomAssigner {

    private final RoomRepository roomRepository;
    private final TagRepository tagRepository;
    private final TagScoringEngine tagScoringEngine;
    private final QuotaPolicy quotaPolicy;

    public RoomAssigner(RoomRepository roomRepository) {
        this(roomRepository, new TagRepository(), new QuotaPolicy());
    }

    public RoomAssigner(RoomRepository roomRepository, QuotaPolicy quotaPolicy) {
        this(roomRepository, new TagRepository(), quotaPolicy);
    }

    public RoomAssigner(RoomRepository roomRepository, TagRepository tagRepository, QuotaPolicy quotaPolicy) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.tagRepository = (tagRepository != null) ? tagRepository : new TagRepository();
        this.quotaPolicy = (quotaPolicy != null) ? quotaPolicy : new QuotaPolicy();
        this.tagScoringEngine = new TagScoringEngine();
    }

    public Optional<Room> assign(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation은 필수입니다.");

        if (reservation.isAssigned()) {
            return roomRepository.findByRoomNumber(reservation.getAssignedRoomNumber());
        }

        StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
        TagPreference tagPref = reservation.getTagPreference();
        RoomType bookedType = reservation.getBookedRoomType();

        // 1. 해당 기간 동일 타입 물리적 공실 추출 (전체 구간 공실)
        List<Room> candidates = roomRepository.findAll().stream()
                .filter(room -> room.isAvailable(targetPeriod))
                .filter(room -> room.getRoomType() == bookedType)
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // 2. [논리 오류 수정] 타입별 킵 방어: 전체 연박 교집합이 아닌 '투숙 기간 각 일자별 최소 공실 수'를 검증
        int typeHoldQuota = quotaPolicy.getTypeHoldQuota(bookedType);
        long minDailyVacant = calculateMinDailyVacant(bookedType, reservation.getCheckInDate(), reservation.getStayNights());
        if (minDailyVacant <= typeHoldQuota) {
            return Optional.empty();
        }

        // 3. [태그별 킵 방어 필터링]
        List<Room> allocatableCandidates = candidates.stream()
                .filter(room -> isRoomAllocatableUnderQuota(room, candidates, tagPref))
                .toList();

        if (allocatableCandidates.isEmpty()) {
            return Optional.empty();
        }

        GuestPreference pref = reservation.getPreference();
        int stayNights = reservation.getStayNights();

        // 4. Soft Scoring 최적 객실 선별
        Optional<Room> bestRoomOpt = allocatableCandidates.stream()
                .max(Comparator.comparingInt(room -> calculateScore(room, pref, tagPref, stayNights)));

        bestRoomOpt.ifPresent(bestRoom -> {
            bestRoom.bookPeriod(targetPeriod);
            reservation.assignRoom(bestRoom.getRoomNumber());
        });

        return bestRoomOpt;
    }

    /**
     * 특정 투숙 기간 동안 매일의 잔여 공실 중 가장 적은 날(병목 일자)의 공실 수를 계산
     */
    public long calculateMinDailyVacant(RoomType type, LocalDate checkIn, int nights) {
        long minCount = Long.MAX_VALUE;
        for (int i = 0; i < nights; i++) {
            LocalDate day = checkIn.plusDays(i);
            StayPeriod singleDay = new StayPeriod(day, 1);
            long dailyVacant = roomRepository.findAll().stream()
                    .filter(r -> r.getRoomType() == type)
                    .filter(r -> r.isAvailable(singleDay))
                    .count();
            minCount = Math.min(minCount, dailyVacant);
        }
        return minCount == Long.MAX_VALUE ? 0 : minCount;
    }

    /**
     * 고객이 요구한 HARD(필수) 태그 미충족 시, 단순 매진인지 쿼터 킵 때문인지 판별하여 경고 생성
     */
    public List<AssignmentAlert> checkHardRequestAlerts(Reservation reservation, Room assignedRoom) {
        if (reservation == null || assignedRoom == null) return List.of();

        TagPreference tagPref = reservation.getTagPreference();
        if (tagPref == null || tagPref.preferredTags().isEmpty()) return List.of();

        List<AssignmentAlert> alerts = new ArrayList<>();
        StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());

        // [논리 오류 수정] 방금 배정된 본인 방(assignedRoom)을 포함한 공실 후보군으로 계산하여 시점 오차 보정
        List<Room> physicalAvailableRooms = roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == reservation.getBookedRoomType())
                .filter(r -> r.getRoomNumber().equals(assignedRoom.getRoomNumber()) || r.isAvailable(targetPeriod))
                .toList();

        for (String requestedTagCode : tagPref.preferredTags()) {
            Optional<RoomTag> tagOpt = tagRepository.findByCode(requestedTagCode);

            if (tagOpt.isPresent() && tagOpt.get().strictness().isHard()) {
                RoomTag hardTag = tagOpt.get();

                if (!assignedRoom.hasTag(hardTag.code())) {
                    long physicalMatchCount = physicalAvailableRooms.stream()
                            .filter(r -> r.hasTag(hardTag.code()))
                            .count();

                    int holdQuota = quotaPolicy.getTagHoldQuota(hardTag.code());

                    String detailReason;
                    if (physicalMatchCount > 0 && physicalMatchCount <= holdQuota) {
                        detailReason = String.format("물리적 공실(%d실)이 존재하나 호텔 안전 쿼터(Hold Quota: %d실)에 의해 차선 배정됨",
                                physicalMatchCount, holdQuota);
                    } else {
                        detailReason = "해당 필수 요청 조건을 충족하는 객실 매진으로 차선 객실에 배정됨";
                    }

                    alerts.add(new AssignmentAlert(
                            reservation,
                            assignedRoom.getRoomNumber(),
                            hardTag.name(),
                            detailReason
                    ));
                }
            }
        }

        return alerts;
    }

    private boolean isRoomAllocatableUnderQuota(Room room, List<Room> sameTypeAvailableRooms, TagPreference tagPref) {
        for (String roomTag : room.getTags()) {
            int holdQuota = quotaPolicy.getTagHoldQuota(roomTag);
            if (holdQuota <= 0) continue;

            long remainingTagRooms = sameTypeAvailableRooms.stream()
                    .filter(r -> r.hasTag(roomTag))
                    .count();

            if (remainingTagRooms <= holdQuota) {
                if (tagPref == null || !tagPref.preferredTags().contains(roomTag)) {
                    return false;
                }
            }
        }
        return true;
    }

    public int calculateScore(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        int totalScore = 0;

        if (tagPref != null && !tagPref.isEmpty()) {
            totalScore += tagScoringEngine.calculateScore(room, tagPref, stayNights);
        } else {
            totalScore += calculateScore(room, pref, stayNights);
        }

        totalScore += calculateConservativeFloorAdjustment(room, pref, tagPref, stayNights);
        return totalScore;
    }

    private int calculateConservativeFloorAdjustment(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        boolean requestedHighFloor = (pref != null && pref.getFloorPref() == FloorPref.HIGH)
                || (tagPref != null && (
                tagPref.preferredTags().contains(RoomTag.HIGH_FLOOR.code())
                        || tagPref.preferredTags().contains(RoomTag.VIEW_TOKYO_TOWER.code())
        ));

        if (!requestedHighFloor && stayNights < 5) {
            if (room.getFloor() >= 10) return -25;
            if (stayNights <= 2 && room.getFloor() <= 6) return 15;
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

    public QuotaPolicy getQuotaPolicy() {
        return quotaPolicy;
    }

    public RoomRepository getRoomRepository() {
        return roomRepository;
    }
}