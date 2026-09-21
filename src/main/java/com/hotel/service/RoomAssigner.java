package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.StayPeriod;
import com.hotel.domain.TagPreference;
import com.hotel.repository.RoomRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RoomAssigner {

    private final RoomRepository roomRepository;
    // [신규] 태그 스위칭 점수 계산기 장착
    private final TagScoringEngine tagScoringEngine;

    public RoomAssigner(RoomRepository roomRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.tagScoringEngine = new TagScoringEngine();
    }

    public Optional<Room> assign(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation은 필수입니다.");

        // 이미 배정된 경우 기존 객실 반환
        if (reservation.isAssigned()) {
            return roomRepository.findByRoomNumber(reservation.getAssignedRoomNumber());
        }

        StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());

        // Pass 1: Hard Filter (요청 기간 공실 검증 및 계약 룸타입 검증)
        List<Room> candidates = roomRepository.findAll().stream()
                .filter(room -> room.isAvailable(targetPeriod))
                .filter(room -> room.getRoomType() == reservation.getBookedRoomType())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Pass 2: Soft Scoring (고객 선호도 + 태그 스위치 점수 + 연박 가중치)
        GuestPreference pref = reservation.getPreference();
        int stayNights = reservation.getStayNights();

        // 예약 객체에 태그 선호도가 있으면 가져오고, 없으면 빈 선호도로 처리
        TagPreference tagPref = TagPreference.empty();

        Optional<Room> bestRoomOpt = candidates.stream()
                .max(Comparator.comparingInt(room -> calculateScore(room, pref, tagPref, stayNights)));

        // 배정 확정: 기간 등록 및 예약 객실 번호 동기화
        bestRoomOpt.ifPresent(bestRoom -> {
            bestRoom.bookPeriod(targetPeriod);
            reservation.assignRoom(bestRoom.getRoomNumber());
        });

        return bestRoomOpt;
    }

    /**
     * [신규] 기존 룰 기반 점수 + 동적 태그 스위칭 매칭 점수 통합 계산
     */
    public int calculateScore(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        int baseRuleScore = calculateScore(room, pref, stayNights);
        int tagScore = tagScoringEngine.calculateScore(room, tagPref, stayNights);
        return baseRuleScore + tagScore;
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
}