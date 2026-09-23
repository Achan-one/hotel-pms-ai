package com.hotel.service;

import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import com.hotel.domain.TagStrictness;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TagScoringEngineTest {

    private TagRepository tagRepository;
    private TagScoringEngine engine;

    @BeforeEach
    void setUp() {
        tagRepository = new InMemoryTagRepository();
        // 가중치 50점짜리 도쿄타워 커스텀 태그 등록
        RoomTag tokyoTower = new RoomTag(
                "VIEW_TOKYO_TOWER", "도쿄타워 전망", "도쿄타워가 보이는 객실",
                RoomTag.TagCategory.VIEW, TagStrictness.SOFT, 50
        );
        tagRepository.save(tokyoTower);

        engine = new TagScoringEngine(tagRepository);
    }

    @Test
    @DisplayName("[동적 가중치 반영] 커스텀 태그의 defaultWeight(50점)와 HIGH_FLOOR(15점)가 합산되어 정확히 채점되어야 한다")
    void calculateScore_DynamicWeight() {
        // 12층 01호: 고층(HIGH_FLOOR, 15점), 코너룸(CORNER_ROOM) 자동 보유
        Room room = new Room("1201", 12, RoomType.SUPERIOR_TWIN, false, true);
        room.addTag("VIEW_TOKYO_TOWER");

        // AI 스위치: 도쿄타워 전망 희망, 엘리베이터 인접 기피
        TagPreference pref = new TagPreference(
                Set.of("VIEW_TOKYO_TOWER", RoomTag.HIGH_FLOOR.code()),
                Set.of(RoomTag.NEAR_ELEVATOR.code())
        );

        int score = engine.calculateScore(room, pref, 1);

        // VIEW_TOKYO_TOWER(+50) + HIGH_FLOOR(+15) = 65점
        assertEquals(65, score);
    }

    @Test
    @DisplayName("[기피 태그 가중치 비례 감점] 가중치 50점짜리 태그를 기피할 경우 1.5배(-75점) 감점되어야 한다")
    void calculateScore_AvoidProportionalPenalty() {
        Room room = new Room("1401", 14, RoomType.SUPERIOR_TWIN, false, true);
        room.addTag("VIEW_TOKYO_TOWER");

        TagPreference pref = new TagPreference(
                Set.of(),
                Set.of("VIEW_TOKYO_TOWER")
        );

        int score = engine.calculateScore(room, pref, 1);

        // 50 * 1.5 = -75점 감점
        assertEquals(-75, score);
    }
}