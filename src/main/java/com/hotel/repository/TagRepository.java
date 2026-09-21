package com.hotel.repository;

import com.hotel.domain.RoomTag;

import java.util.List;
import java.util.Optional;

/**
 * 객실 태그 카탈로그 및 AI 프롬프트 사전 생성을 위한 표준 저장소 인터페이스.
 */
public interface TagRepository {

    void save(RoomTag tag);

    Optional<RoomTag> findByCode(String code);

    List<RoomTag> findAll();

    String buildPromptTagDictionary();
}