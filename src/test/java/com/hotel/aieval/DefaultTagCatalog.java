package com.hotel.aieval;

import com.hotel.domain.RoomTag;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryTagRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 평가에 쓰는 태그 사전. 호텔마다 다른 커스텀 태그가 섞이면 결과가 환경에 따라 달라지므로
 * 시스템 기본 태그 7개만 쓴다. DB나 다른 테스트가 등록한 태그의 영향을 받지 않는다.
 */
public final class DefaultTagCatalog {

    public static final List<RoomTag> TAGS = List.of(
            RoomTag.HIGH_FLOOR, RoomTag.LOW_FLOOR, RoomTag.NEAR_ELEVATOR, RoomTag.AWAY_FROM_ELEVATOR,
            RoomTag.CORNER_ROOM, RoomTag.QUIET_ZONE, RoomTag.ACCESSIBLE);

    public static final Set<String> CODES = TAGS.stream().map(RoomTag::code).collect(Collectors.toUnmodifiableSet());

    private DefaultTagCatalog() {
    }

    /** 기본 태그 7개만 든 새 저장소. */
    public static TagRepository newRepository() {
        // 인메모리 저장소의 기본 시드와 무관하게 정확히 7개로 맞춘다.
        InMemoryTagRepository repository = new InMemoryTagRepository();
        repository.findAll().forEach(tag -> repository.deleteByCode(tag.code()));
        TAGS.forEach(repository::save);
        return repository;
    }
}
