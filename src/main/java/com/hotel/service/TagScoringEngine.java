package com.hotel.service;

import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.TagPreference;
import com.hotel.repository.TagRepository;

import java.util.Optional;
import java.util.Set;

public class TagScoringEngine {

    private final TagRepository tagRepository;
    private static final int DEFAULT_BASE_SCORE = 20;
    private static final int CONFLICT_PENALTY_SCORE = 45; // 정반대 물리 조건 불일치 감점

    public TagScoringEngine(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public TagScoringEngine() {
        this(null);
    }

    public int calculateScore(Room room, TagPreference tagPref, int stayNights) {
        if (room == null || tagPref == null || tagPref.isEmpty()) {
            return 0;
        }

        int score = 0;
        Set<String> roomTags = room.getTags();
        Set<String> prefTags = tagPref.preferredTags();

        // 1. 선호 태그 매칭 (+) : 커스텀/기본 태그의 defaultWeight를 동적으로 가져와 가산
        for (String prefTag : prefTags) {
            String upperCode = prefTag.toUpperCase();
            if (roomTags.contains(upperCode)) {
                int tagWeight = DEFAULT_BASE_SCORE;
                if (tagRepository != null) {
                    Optional<RoomTag> tagOpt = tagRepository.findByCode(upperCode);
                    if (tagOpt.isPresent() && tagOpt.get().defaultWeight() > 0) {
                        tagWeight = tagOpt.get().defaultWeight(); // 👈 관리자 지정 가중치 즉시 반영!
                    }
                }
                score += tagWeight;
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

        // 3. 기피 태그 스위치가 켜진 항목 매칭 시 감점 (-) : 해당 태그 가중치의 1.5배 비례 페널티 (최소 30점 감점)
        for (String avoidTag : tagPref.avoidTags()) {
            String upperCode = avoidTag.toUpperCase();
            if (roomTags.contains(upperCode)) {
                int penalty = 30;
                if (tagRepository != null) {
                    Optional<RoomTag> tagOpt = tagRepository.findByCode(upperCode);
                    if (tagOpt.isPresent() && tagOpt.get().defaultWeight() > 0) {
                        penalty = Math.max(30, (int) (tagOpt.get().defaultWeight() * 1.5));
                    }
                }
                score -= penalty;
            }
        }

        // 4. 연박 가중치: 점수가 양수일 때만 1.3배 증폭 (음수 감점이 완화되지 않도록 방어)
        if (stayNights >= 3 && score > 0) {
            score = (int) (score * 1.3);
        }

        return score;
    }
}