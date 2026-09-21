package com.hotel.service;

import com.hotel.domain.Room;
import com.hotel.domain.TagPreference;

import java.util.Set;

public class TagScoringEngine {

    private static final int DEFAULT_MATCH_SCORE = 20;  // 선호 태그 일치 시 +20점
    private static final int DEFAULT_PENALTY_SCORE = 30; // 기피 태그 포함 시 -30점

    public int calculateScore(Room room, TagPreference tagPref, int stayNights) {
        if (room == null || tagPref == null || tagPref.isEmpty()) {
            return 0;
        }

        int score = 0;
        Set<String> roomTags = room.getTags();

        // 1. 선호 태그 스위치가 켜진 항목 매칭 (+)
        for (String prefTag : tagPref.preferredTags()) {
            if (roomTags.contains(prefTag.toUpperCase())) {
                score += DEFAULT_MATCH_SCORE;
            }
        }

        // 2. 기피 태그 스위치가 켜진 항목 매칭 시 감점 (-)
        for (String avoidTag : tagPref.avoidTags()) {
            if (roomTags.contains(avoidTag.toUpperCase())) {
                score -= DEFAULT_PENALTY_SCORE;
            }
        }

        // 3. 연박 가중치: 장기 체류객(3박 이상)은 태그 매칭 점수를 1.3배 증폭
        if (stayNights >= 3 && score > 0) {
            score = (int) (score * 1.3);
        }

        return score;
    }
}