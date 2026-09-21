package com.hotel.service;

import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.TagPreference;

import java.util.Set;

public class TagScoringEngine {

    private static final int DEFAULT_MATCH_SCORE = 20;    // 선호 태그 일치 시 +20점
    private static final int DEFAULT_PENALTY_SCORE = 30;  // 기피 태그 포함 시 -30점
    private static final int CONFLICT_PENALTY_SCORE = 45; // 정반대 물리 조건 불일치 시 -45점

    public int calculateScore(Room room, TagPreference tagPref, int stayNights) {
        if (room == null || tagPref == null || tagPref.isEmpty()) {
            return 0;
        }

        int score = 0;
        Set<String> roomTags = room.getTags();
        Set<String> prefTags = tagPref.preferredTags();

        // 1. 선호 태그 스위치가 켜진 항목 매칭 (+)
        for (String prefTag : prefTags) {
            if (roomTags.contains(prefTag.toUpperCase())) {
                score += DEFAULT_MATCH_SCORE;
            }
        }

        // 2. 상반되는 물리적 조건 불일치 강력 감점 (-)
        // 저층(6층 이하)을 원하는데 10층 이상 고층 방인 경우
        if (prefTags.contains(RoomTag.LOW_FLOOR.code()) && room.getFloor() >= 10) {
            score -= CONFLICT_PENALTY_SCORE;
        }
        // 고층(10층 이상)을 원하는데 6층 이하 저층 방인 경우
        if (prefTags.contains(RoomTag.HIGH_FLOOR.code()) && room.getFloor() <= 6) {
            score -= CONFLICT_PENALTY_SCORE;
        }
        // 엘리베이터 인접을 원하는데 엘리베이터에서 먼 방인 경우
        if (prefTags.contains(RoomTag.NEAR_ELEVATOR.code()) && !room.isNearElevator()) {
            score -= CONFLICT_PENALTY_SCORE;
        }

        // 3. 기피 태그 스위치가 켜진 항목 매칭 시 감점 (-)
        for (String avoidTag : tagPref.avoidTags()) {
            if (roomTags.contains(avoidTag.toUpperCase())) {
                score -= DEFAULT_PENALTY_SCORE;
            }
        }

        // 4. 연박 가중치: 점수가 양수일 때만 1.3배 증폭 (음수 감점이 깎이지 않도록 방어)
        if (stayNights >= 3 && score > 0) {
            score = (int) (score * 1.3);
        }

        return score;
    }
}