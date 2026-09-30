package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.TagPreference;

/**
 * 점수 내역 도입 전의 RoomAssigner 점수 계산(개인 선호, 층 보정, 최종 합산)을 그대로 옮겨 둔 기준 구현.
 * 리팩터링 뒤의 최종 점수가 옛 계산과 한 점도 다르지 않은지 비교하는 데만 쓴다. 운영 코드에서 쓰지 않는다.
 */
final class ReferenceRoomScoring {

    private final ReferenceTagScoring tagScoring;

    ReferenceRoomScoring(ReferenceTagScoring tagScoring) {
        this.tagScoring = tagScoring;
    }

    int score(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        int totalScore = 0;

        if (tagPref != null && !tagPref.isEmpty()) {
            totalScore += tagScoring.score(room, tagPref, stayNights);
        } else {
            totalScore += legacy(room, pref, stayNights);
        }

        totalScore += floorAdjustment(room, pref, tagPref, stayNights);
        return totalScore;
    }

    int floorAdjustment(Room room, GuestPreference pref, TagPreference tagPref, int stayNights) {
        boolean requestedHighFloor = (pref != null && pref.getFloorPref() == FloorPref.HIGH)
                || (tagPref != null && (
                tagPref.preferredTags().contains(RoomTag.HIGH_FLOOR.code())
                        || tagPref.preferredTags().contains("VIEW_TOKYO_TOWER")
        ));

        if (!requestedHighFloor && stayNights < 5) {
            if (room.getFloor() >= 10) return -25;
            if (stayNights <= 2 && room.getFloor() <= 6) return 15;
        }

        return 0;
    }

    int legacy(Room room, GuestPreference pref, int stayNights) {
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
}
