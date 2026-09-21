package com.hotel.repository.memory;

import com.hotel.domain.RoomTag;
import com.hotel.repository.TagRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap 기반 인메모리 태그 저장소 구현체.
 */
public class InMemoryTagRepository implements TagRepository {

    private final Map<String, RoomTag> tagStore = new ConcurrentHashMap<>();

    public InMemoryTagRepository() {
        initDefaultTags();
    }

    @Override
    public void save(RoomTag tag) {
        Objects.requireNonNull(tag, "저장할 태그는 null일 수 없습니다.");
        tagStore.put(tag.code().toUpperCase(), tag);
    }

    @Override
    public Optional<RoomTag> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(tagStore.get(code.trim().toUpperCase()));
    }

    @Override
    public List<RoomTag> findAll() {
        return new ArrayList<>(tagStore.values());
    }

    @Override
    public String buildPromptTagDictionary() {
        if (tagStore.isEmpty()) {
            return "(등록된 태그 없음)";
        }

        StringBuilder sb = new StringBuilder();
        for (RoomTag tag : tagStore.values()) {
            sb.append(String.format("- %s: %s (분류: %s | 엄격도: %s | 설명: %s, 기본점수: %d점)\n",
                    tag.code(), tag.name(), tag.category().getDesc(), tag.strictness().getTitle(),
                    tag.description(), tag.defaultWeight()));
        }
        return sb.toString();
    }

    private void initDefaultTags() {
        save(RoomTag.HIGH_FLOOR);
        save(RoomTag.LOW_FLOOR);
        save(RoomTag.NEAR_ELEVATOR);
        save(RoomTag.AWAY_FROM_ELEVATOR);
        save(RoomTag.CORNER_ROOM);
        save(RoomTag.QUIET_ZONE);
    }
}