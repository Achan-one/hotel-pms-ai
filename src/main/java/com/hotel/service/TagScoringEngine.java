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
        if (room == null) {
            return 0;
        }

        int score = 0;
        Set<String> roomTags = room.getTags();
        Set<String> prefTags = (tagPref != null) ? tagPref.preferredTags() : Set.of();
        Set<String> avoidTags = (tagPref != null) ? tagPref.avoidTags() : Set.of();

        // 1. 선호 태그 매칭 (+) : 가중치 가산
        for (String prefTag : prefTags) {
            String upperCode = prefTag.toUpperCase();
            if (roomTags.contains(upperCode)) {
                int tagWeight = DEFAULT_BASE_SCORE;
                if (tagRepository != null) {
                    Optional<RoomTag> tagOpt = tagRepository.findByCode(upperCode);
                    if (tagOpt.isPresent() && tagOpt.get().defaultWeight() > 0) {
                        tagWeight = tagOpt.get().defaultWeight();
                    }
                }
                score += tagWeight;
            }
        }

        // 2. 상반되는 물리적 조건 불일치 강력 감점 (-)
        if (prefTags.contains(RoomTag.LOW_FLOOR.code()) && room.getFloor() >= 10) {
            score -= CONFLICT_PENALTY_SCORE;
        }
        if (prefTags.contains(RoomTag.HIGH_FLOOR.code()) && room.getFloor() <= 6) {
            score -= CONFLICT_PENALTY_SCORE;
        }
        if (prefTags.contains(RoomTag.NEAR_ELEVATOR.code()) && !room.isNearElevator()) {
            score -= CONFLICT_PENALTY_SCORE;
        }

        // 3. 기피 태그 스위치가 켜진 항목 매칭 시 감점 (-) : 해당 태그 가중치의 1.5배 비례 페널티 (최소 30점 감점)
        for (String avoidTag : avoidTags) {
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

        // 💡 4. [핵심 추가] 엄근진한 특수 태그 낭비 방지 감점 (Tag Waste / Inventory Preservation)
        // 고객이 요구하지 않은 특수 마케팅 태그나 희소 태그를 방이 가지고 있다면, 그 방의 가치를 보존하기 위해 감점 부여
        for (String roomTagCode : roomTags) {
            // 건축 도면 기본 물리 태그(고층, 저층, 엘베인접, 코너 등)는 일반 방에도 기본 분포하므로 낭비 감점에서 제외
            if (isPervasivePhysicalTag(roomTagCode)) {
                continue;
            }

            // 도쿄타워, 배리어프리, 아로마룸 등 특별 태그를 요구하지 않은 고객인 경우 감점!
            if (!prefTags.contains(roomTagCode)) {
                int wastePenalty = 30; // 기본 감점
                if (tagRepository != null) {
                    Optional<RoomTag> tagOpt = tagRepository.findByCode(roomTagCode);
                    if (tagOpt.isPresent() && tagOpt.get().defaultWeight() > 0) {
                        wastePenalty = tagOpt.get().defaultWeight(); // 해당 태그의 가중치만큼 감점!
                    }
                }
                score -= wastePenalty;
            }
        }

        // 5. 연박 가중치: 점수가 양수일 때만 1.3배 증폭 (음수 감점이 완화되지 않도록 방어)
        if (stayNights >= 3 && score > 0) {
            score = (int) (score * 1.3);
        }

        return score;
    }

    // 전 층에 흔하게 깔려있는 기본 물리 태그인지 판별 (이 태그들은 낭비 감점에서 제외)
    private boolean isPervasivePhysicalTag(String tagCode) {
        return RoomTag.HIGH_FLOOR.code().equals(tagCode)
                || RoomTag.LOW_FLOOR.code().equals(tagCode)
                || RoomTag.NEAR_ELEVATOR.code().equals(tagCode)
                || RoomTag.AWAY_FROM_ELEVATOR.code().equals(tagCode)
                || RoomTag.CORNER_ROOM.code().equals(tagCode)
                || RoomTag.QUIET_ZONE.code().equals(tagCode);
    }
}