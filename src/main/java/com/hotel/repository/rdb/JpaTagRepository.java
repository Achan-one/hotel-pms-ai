package com.hotel.repository.rdb;

import com.hotel.domain.RoomTag;
import com.hotel.entity.RoomTagEntity;
import com.hotel.repository.TagRepository;
import com.hotel.repository.jpa.SpringDataTagRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JpaTagRepository implements TagRepository {

    private final SpringDataTagRepository jpaRepo;

    public JpaTagRepository(SpringDataTagRepository jpaRepo) {
        this.jpaRepo = Objects.requireNonNull(jpaRepo);
    }

    @Override
    @Transactional
    public void save(RoomTag tag) {
        Objects.requireNonNull(tag, "저장할 태그는 null일 수 없습니다.");
        jpaRepo.save(RoomTagEntity.fromDomain(tag));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomTag> findByCode(String code) {
        if (code == null) return Optional.empty();
        return jpaRepo.findById(code.trim().toUpperCase()).map(RoomTagEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomTag> findAll() {
        return jpaRepo.findAll().stream().map(RoomTagEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String buildPromptTagDictionary() {
        List<RoomTag> tags = findAll();
        if (tags.isEmpty()) return "(등록된 태그 없음)";

        StringBuilder sb = new StringBuilder();
        for (RoomTag tag : tags) {
            sb.append(String.format("- %s: %s (분류: %s | 엄격도: %s | 설명: %s, 기본점수: %d점)\n",
                    tag.code(), tag.name(), tag.category().getDesc(), tag.strictness().getTitle(),
                    tag.description(), tag.defaultWeight()));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public void deleteByCode(String code) {
        if (code != null) {
            jpaRepo.deleteById(code.trim().toUpperCase());
        }
    }
}