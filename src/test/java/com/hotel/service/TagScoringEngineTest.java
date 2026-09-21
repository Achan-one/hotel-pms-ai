package com.hotel.service;

import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TagScoringEngineTest {

    private final TagScoringEngine engine = new TagScoringEngine();

    @Test
    @DisplayName("[태그 스위치 매칭] 선호 태그가 일치하면 가산점을 받고, 기피 태그가 포함되면 감점된다")
    void calculateScore_TagSwitching() {
        // 12층 01호: 고층(HIGH_FLOOR), 코너룸(CORNER_ROOM) 태그 자동 보유
        Room room = new Room("1201", 12, RoomType.SUPERIOR_TWIN, false, true);

        // 사용자가 커스텀 태그 동적 추가
        room.addTag(RoomTag.VIEW_TOKYO_TOWER.code());

        // AI가 메모에서 분석한 스위치: 도쿄타워 전망 희망, 엘리베이터 인접 기피
        TagPreference pref = new TagPreference(
                Set.of(RoomTag.VIEW_TOKYO_TOWER.code(), RoomTag.HIGH_FLOOR.code()),
                Set.of(RoomTag.NEAR_ELEVATOR.code())
        );

        int score = engine.calculateScore(room, pref, 1);

        // VIEW_TOKYO_TOWER(+20) + HIGH_FLOOR(+20) = 40점
        assertEquals(40, score);
    }

    @Test
    @DisplayName("[기피 태그 감점] 기피 태그(NEAR_ELEVATOR)가 방에 존재하면 -30점 감점 처리된다")
    void calculateScore_AvoidPenalty() {
        // 04층 05호: 엘리베이터 인접 방
        Room room = new Room("0405", 4, RoomType.MODERATE_DOUBLE, true, false);

        TagPreference pref = new TagPreference(
                Set.of(RoomTag.LOW_FLOOR.code()), // 저층 희망 (+20)
                Set.of(RoomTag.NEAR_ELEVATOR.code()) // 엘베 기피 (-30)
        );

        int score = engine.calculateScore(room, pref, 1);

        // +20 - 30 = -10점
        assertEquals(-10, score);
    }
}