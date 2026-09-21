package com.hotel.service;

import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.RoomTag;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagStrictness;
import com.hotel.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminTagServiceTest {

    private TagRepository tagRepository;
    private QuotaPolicy quotaPolicy;
    private AdminTagService adminTagService;

    @BeforeEach
    void setUp() {
        tagRepository = new TagRepository();
        quotaPolicy = new QuotaPolicy();
        adminTagService = new AdminTagService(tagRepository, quotaPolicy);
    }

    @Test
    @DisplayName("[보안 권한] 비관리자(isAdmin: false)는 태그 등록, 엄격도 수정, 쿼터 변경 시 SecurityException이 발생해야 한다")
    void nonAdmin_ActionDenied_ThrowsSecurityException() {
        boolean isNotAdmin = false;

        RoomTag customTag = new RoomTag(
                "CUSTOM_TEST", "커스텀태그", "설명", RoomTag.TagCategory.ETC, TagStrictness.SOFT, 10
        );

        // 1. 비관리자 태그 등록 시도 차단
        assertThrows(SecurityException.class, () ->
                adminTagService.registerTag(isNotAdmin, customTag));

        // 2. 비관리자 엄격도 수정 시도 차단
        assertThrows(SecurityException.class, () ->
                adminTagService.updateTagStrictness(isNotAdmin, "HIGH_FLOOR", TagStrictness.HARD));

        // 3. 비관리자 타입별 쿼터 수정 시도 차단
        assertThrows(SecurityException.class, () ->
                adminTagService.updateRoomTypeHoldQuota(isNotAdmin, RoomType.EXECUTIVE_DOUBLE, 5));

        // 4. 비관리자 태그별 쿼터 수정 시도 차단
        assertThrows(SecurityException.class, () ->
                adminTagService.updateTagHoldQuota(isNotAdmin, "VIEW_TOKYO_TOWER", 5));
    }

    @Test
    @DisplayName("[엄격도 동적 변경] 관리자는 태그의 엄격도를 SOFT에서 HARD로 정상 전환할 수 있어야 한다")
    void admin_UpdateTagStrictness_Success() {
        boolean isAdmin = true;

        // HIGH_FLOOR 기본값: SOFT
        RoomTag highFloor = tagRepository.findByCode("HIGH_FLOOR").orElseThrow();
        assertEquals(TagStrictness.SOFT, highFloor.strictness());

        // 어드민이 HARD로 전환
        adminTagService.updateTagStrictness(isAdmin, "HIGH_FLOOR", TagStrictness.HARD);

        RoomTag updated = tagRepository.findByCode("HIGH_FLOOR").orElseThrow();
        assertEquals(TagStrictness.HARD, updated.strictness());
        assertTrue(updated.strictness().isHard());
    }

    @Test
    @DisplayName("[쿼터 동적 변경] 관리자는 객실 타입별 킵과 태그별 킵 수량을 동적으로 변경할 수 있어야 한다")
    void admin_UpdateHoldQuotas_Success() {
        boolean isAdmin = true;

        // 1. 객실 타입 쿼터 변경: 이그제큐티브 더블 1실 -> 3실로 증액
        adminTagService.updateRoomTypeHoldQuota(isAdmin, RoomType.EXECUTIVE_DOUBLE, 3);
        assertEquals(3, quotaPolicy.getTypeHoldQuota(RoomType.EXECUTIVE_DOUBLE));

        // 2. 태그 쿼터 변경: 도쿄타워 전망 2실 -> 0실로 전면 해제
        adminTagService.updateTagHoldQuota(isAdmin, "VIEW_TOKYO_TOWER", 0);
        assertEquals(0, quotaPolicy.getTagHoldQuota("VIEW_TOKYO_TOWER"));
    }
}