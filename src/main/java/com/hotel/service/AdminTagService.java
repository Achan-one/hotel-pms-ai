package com.hotel.service;

import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.RoomTag;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagStrictness;
import com.hotel.repository.TagRepository;

import java.util.Objects;

/**
 * 호텔 관리자(ADMIN) 전용 태그 정책 및 보존 쿼터(Safety Quota) 관리 서비스
 */
public class AdminTagService {

    private final TagRepository tagRepository;
    private final QuotaPolicy quotaPolicy;

    public AdminTagService(TagRepository tagRepository) {
        this(tagRepository, new QuotaPolicy());
    }

    public AdminTagService(TagRepository tagRepository, QuotaPolicy quotaPolicy) {
        this.tagRepository = Objects.requireNonNull(tagRepository, "tagRepository는 필수입니다.");
        this.quotaPolicy = Objects.requireNonNull(quotaPolicy, "quotaPolicy는 필수입니다.");
    }

    public void registerTag(boolean isAdmin, RoomTag tag) {
        validateAdminRole(isAdmin);
        tagRepository.save(tag);
    }

    public void updateTagStrictness(boolean isAdmin, String tagCode, TagStrictness strictness) {
        validateAdminRole(isAdmin);
        RoomTag target = tagRepository.findByCode(tagCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다: " + tagCode));

        RoomTag updated = target.withStrictness(strictness);
        tagRepository.save(updated);
    }

    /**
     * [신규] 관리자가 객실 타입별 킵 수량을 동적으로 변경
     */
    public void updateRoomTypeHoldQuota(boolean isAdmin, RoomType type, int quota) {
        validateAdminRole(isAdmin);
        quotaPolicy.setTypeHoldQuota(type, quota);
    }

    /**
     * [신규] 관리자가 특정 태그의 킵 수량을 동적으로 변경
     */
    public void updateTagHoldQuota(boolean isAdmin, String tagCode, int quota) {
        validateAdminRole(isAdmin);
        quotaPolicy.setTagHoldQuota(tagCode, quota);
    }

    public QuotaPolicy getQuotaPolicy() {
        return quotaPolicy;
    }

    private void validateAdminRole(boolean isAdmin) {
        if (!isAdmin) {
            throw new SecurityException("태그 및 보존 쿼터 수정 권한이 없습니다. 호텔 관리자 계정으로 로그인하세요.");
        }
    }
}