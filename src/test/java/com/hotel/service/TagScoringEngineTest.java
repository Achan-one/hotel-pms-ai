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

        // AI 스위치: 도쿄타워 전망 희망, 고층 희망
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

        // 도쿄타워 기피 고객
        TagPreference pref = new TagPreference(
                Set.of(),
                Set.of("VIEW_TOKYO_TOWER")
        );

        int score = engine.calculateScore(room, pref, 1);

        // 기본 태그 낭비 감점(-50) + 기피 패널티(-75) = -125점
        assertTrue(score <= -75, "기피 태그 매칭 시 강력한 비례 감점이 적용되어야 합니다.");
    }

    @Test
    @DisplayName("[태그 낭비 방지 감점] 요청 없는 일반 고객은 특수 태그(도쿄타워)가 있는 방을 평가할 때 감점을 받아야 한다")
    void calculateScore_TagWastePenalty_ForGeneralGuest() {
        // 일반 방: 태그 없음 (기본 물리 태그만 존재)
        Room normalRoom = new Room("0501", 5, RoomType.SUPERIOR_TWIN, false, false);

        // 특수 방: 도쿄타워 전망 태그 보유 (가중치 50)
        Room tokyoTowerRoom = new Room("1401", 14, RoomType.SUPERIOR_TWIN, false, true);
        tokyoTowerRoom.addTag("VIEW_TOKYO_TOWER");

        // 요구사항이 전혀 없는 일반 예약 (TagPreference.empty())
        TagPreference noRequestPref = TagPreference.empty();

        int normalScore = engine.calculateScore(normalRoom, noRequestPref, 1);
        int tokyoTowerScore = engine.calculateScore(tokyoTowerRoom, noRequestPref, 1);

        // 일반 방은 감점이 없어 0점
        assertEquals(0, normalScore);

        // 도쿄타워 방은 해당 태그를 요구하지 않았으므로 가중치(50점)만큼 감점되어 -50점
        assertEquals(-50, tokyoTowerScore);

        // 따라서 일반 고객에게는 일반 방(0점)이 특수 방(-50점)보다 우선순위가 훨씬 높아야 함
        assertTrue(normalScore > tokyoTowerScore, "요청 없는 고객에게는 특수 태그 방보다 일반 방이 우선 배정되어야 합니다.");
    }
}