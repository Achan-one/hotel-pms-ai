package com.hotel.repository;

import com.hotel.domain.RoomTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagRepository {

    // 태그 코드(Key) -> RoomTag 엔티티(Value)
    private final Map<String, RoomTag> tagStore = new ConcurrentHashMap<>();

    public TagRepository() {
        initDefaultTags();
    }

    public void save(RoomTag tag) {
        Objects.requireNonNull(tag, "저장할 태그는 null일 수 없습니다.");
        tagStore.put(tag.code().toUpperCase(), tag);
    }

    public Optional<RoomTag> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(tagStore.get(code.trim().toUpperCase()));
    }

    public List<RoomTag> findAll() {
        return new ArrayList<>(tagStore.values());
    }

    /**
     * [핵심] 프롬프트에 자동으로 주입될 동적 태그 사전 문자열 생성
     * 사용자가 새로 등록한 태그와 설명이 여기에 실시간으로 반영됩니다.
     */
    public String buildPromptTagDictionary() {
        if (tagStore.isEmpty()) {
            return "(등록된 태그 없음)";
        }

        StringBuilder sb = new StringBuilder();
        for (RoomTag tag : tagStore.values()) {
            sb.append(String.format("- %s: %s (설명: %s, 기본점수: %d점)\n",
                    tag.code(), tag.name(), tag.description(), tag.defaultWeight()));
        }
        return sb.toString();
    }

    private void initDefaultTags() {
        save(new RoomTag("HIGH_FLOOR", "고층", "10층 이상의 상층부 객실. 고층 전망, 뷰 선호", RoomTag.TagCategory.FLOOR, 15));
        save(new RoomTag("LOW_FLOOR", "저층", "6층 이하 저층 객실. 보행 편의, 어르신/유아 동반", RoomTag.TagCategory.FLOOR, 15));
        save(new RoomTag("NEAR_ELEVATOR", "엘리베이터 인접", "엘리베이터와 가까워 이동이 편함", RoomTag.TagCategory.LOCATION, 20));
        save(new RoomTag("AWAY_FROM_ELEVATOR", "엘리베이터 이격", "엘리베이터와 멀리 떨어진 복도 안쪽 방. 소음 차단", RoomTag.TagCategory.LOCATION, 20));
        save(new RoomTag("CORNER_ROOM", "코너룸", "건물 모퉁이 끝방. 2면 창문, 조용함", RoomTag.TagCategory.VIEW, 15));
        save(new RoomTag("QUIET_ZONE", "조용한 방", "소음 민감 고객, 아기 동반, 수면 방해 최소화", RoomTag.TagCategory.NOISE, 25));
    }
}