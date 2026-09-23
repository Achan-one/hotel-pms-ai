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
        Room room = new Room("1201", 12, RoomType.SUPERIOR_TWIN, false, true);

        // 상수가 아닌 문자열 코드로 커스텀 태그 추가
        room.addTag("VIEW_TOKYO_TOWER");

        TagPreference pref = new TagPreference(
                Set.of("VIEW_TOKYO_TOWER", RoomTag.HIGH_FLOOR.code()),
                Set.of(RoomTag.NEAR_ELEVATOR.code())
        );

        int score = engine.calculateScore(room, pref, 1);

        assertEquals(40, score);
    }

    @Test
    @DisplayName("[기피 태그 감점] 기피 태그(NEAR_ELEVATOR)가 방에 존재하면 -30점 감점 처리된다")
    void calculateScore_AvoidPenalty() {
        Room room = new Room("0405", 4, RoomType.MODERATE_DOUBLE, true, false);

        TagPreference pref = new TagPreference(
                Set.of(RoomTag.LOW_FLOOR.code()),
                Set.of(RoomTag.NEAR_ELEVATOR.code())
        );

        int score = engine.calculateScore(room, pref, 1);

        assertEquals(-10, score);
    }
}