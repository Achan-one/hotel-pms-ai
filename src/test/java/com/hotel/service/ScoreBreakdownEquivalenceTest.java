package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import com.hotel.service.dto.ScoreBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 점수 내역 도입 리팩터링이 배정 결과를 바꾸지 않았는지 확인한다.
 * 새 엔진의 총점과 항목 합계를 옛 알고리즘(ReferenceTagScoring)과 무작위 입력으로 비교한다.
 */
class ScoreBreakdownEquivalenceTest {

    private static final List<String> TAG_POOL = List.of(
            "HIGH_FLOOR", "LOW_FLOOR", "NEAR_ELEVATOR", "AWAY_FROM_ELEVATOR", "CORNER_ROOM", "QUIET_ZONE",
            "ACCESSIBLE", "VIEW_TOKYO_TOWER", "AROMA_ROOM", "UNKNOWN_TAG");

    private final InMemoryTagRepository tags = customizedTags();
    private final TagScoringEngine engine = new TagScoringEngine(tags);
    private final ReferenceTagScoring reference = new ReferenceTagScoring(tags);
    private final RoomAssigner assigner = new RoomAssigner(new InMemoryRoomRepository(), tags, null);
    private final ReferenceRoomScoring referenceRoom = new ReferenceRoomScoring(reference);

    private static InMemoryTagRepository customizedTags() {
        InMemoryTagRepository repo = new InMemoryTagRepository();
        repo.save(new RoomTag("VIEW_TOKYO_TOWER", "도쿄타워 전망", "전망", RoomTag.TagCategory.VIEW, com.hotel.domain.TagStrictness.SOFT, 35, false));
        repo.save(new RoomTag("AROMA_ROOM", "아로마룸", "특수", RoomTag.TagCategory.AMENITY, com.hotel.domain.TagStrictness.SOFT, 0, false));
        repo.save(RoomTag.ACCESSIBLE);
        return repo;
    }

    private Set<String> randomTags(Random random, int max) {
        Set<String> picked = new HashSet<>();
        int count = random.nextInt(max + 1);
        for (int i = 0; i < count; i++) {
            picked.add(TAG_POOL.get(random.nextInt(TAG_POOL.size())));
        }
        return picked;
    }

    private Room randomRoom(Random random) {
        int floor = 3 + random.nextInt(13);
        Room room = new Room(String.format("%02d%02d", floor, 1 + random.nextInt(16)), floor,
                RoomType.values()[random.nextInt(RoomType.values().length)], random.nextBoolean(), random.nextBoolean());
        randomTags(random, 5).forEach(room::addTag);
        return room;
    }

    @Test
    @DisplayName("[점수 내역] 태그 점수: 새 엔진의 총점이 옛 알고리즘과 무작위 입력 20,000건에서 모두 같고, 항목 합계와도 같다")
    void tagScoreMatchesReferenceImplementation() {
        Random random = new Random(20260920L);
        for (int i = 0; i < 20_000; i++) {
            Room room = randomRoom(random);
            TagPreference pref = new TagPreference(randomTags(random, 3), randomTags(random, 3));
            int nights = 1 + random.nextInt(6);

            int expected = reference.score(room, pref, nights);
            ScoreBreakdown breakdown = engine.explain(room, pref, nights);

            assertEquals(expected, engine.calculateScore(room, pref, nights), "총점 불일치 #" + i);
            assertEquals(expected, breakdown.total(), "항목 합계 불일치 #" + i);
            assertEquals(expected, breakdown.totalsByCategory().values().stream().mapToInt(Integer::intValue).sum());
        }
    }

    @Test
    @DisplayName("[점수 내역] RoomAssigner의 최종 점수(층 보정, 개인 선호 포함)가 옛 계산과 무작위 입력 20,000건에서 모두 같고, 항목 합계와도 같다")
    void assignerScoreEqualsSumOfComponents() {
        Random random = new Random(1234L);
        for (int i = 0; i < 20_000; i++) {
            Room room = randomRoom(random);
            GuestPreference guest = new GuestPreference(
                    FloorPref.values()[random.nextInt(3)], ElevatorPref.values()[random.nextInt(3)],
                    CornerPref.values()[random.nextInt(3)], random.nextBoolean());
            TagPreference tagPref = random.nextBoolean() ? TagPreference.empty() : new TagPreference(randomTags(random, 3), randomTags(random, 3));
            int nights = 1 + random.nextInt(7);

            ScoreBreakdown breakdown = assigner.explainScore(room, guest, tagPref, nights);

            int expected = referenceRoom.score(room, guest, tagPref, nights);
            assertEquals(expected, assigner.calculateScore(room, guest, tagPref, nights), "최종 점수 불일치 #" + i);
            assertEquals(expected, breakdown.total(), "항목 합계 불일치 #" + i);
        }
    }

    @Test
    @DisplayName("[점수 내역] 개인 선호(태그 없음) 점수도 옛 계산과 같다")
    void guestPreferenceScoreKeepsItsFormula() {
        Room room = new Room("1005", 10, RoomType.SUPERIOR_TWIN, false, true);
        GuestPreference pref = new GuestPreference(FloorPref.HIGH, ElevatorPref.AWAY, CornerPref.PREFER, true);

        // 고층 +15, 엘리베이터 이격 +20, 코너 +10, 조용한 방(이격 +15, 코너 +10) = 70
        assertEquals(70, assigner.calculateScore(room, pref, 1));
        // 3박: 70 x 1.3 = 91, 연박 보너스(이격 +10, 코너 +5) = 106
        assertEquals(106, assigner.calculateScore(room, pref, 3));
    }

    @Test
    @DisplayName("[점수 내역] 항목이 어떤 규칙에서 나온 점수인지 설명과 함께 담긴다")
    void componentsCarryCategoryAndDetail() {
        Room room = new Room("0505", 5, RoomType.SUPERIOR_TWIN, true, false);
        room.addTag("HIGH_FLOOR");
        room.addTag("VIEW_TOKYO_TOWER");
        TagPreference pref = new TagPreference(Set.of("HIGH_FLOOR"), Set.of());

        ScoreBreakdown breakdown = engine.explain(room, pref, 1);

        // 선호 HIGH_FLOOR 일치 +15, 5층인데 고층 요청 -45, 요청하지 않은 특수 태그(도쿄타워) 낭비 -35
        assertEquals(-65, breakdown.total());
        assertEquals(15, breakdown.totalsByCategory().get(ScoreBreakdown.Category.PREFERRED_MATCH));
        assertEquals(-45, breakdown.totalsByCategory().get(ScoreBreakdown.Category.CONFLICT));
        assertEquals(-35, breakdown.totalsByCategory().get(ScoreBreakdown.Category.TAG_WASTE));
        String text = breakdown.describe();
        assertTrue(text.contains("선호 태그 가산(HIGH_FLOOR): +15"), text);
        assertTrue(text.contains("특수 태그 낭비 감점(VIEW_TOKYO_TOWER): -35"), text);
    }
}
