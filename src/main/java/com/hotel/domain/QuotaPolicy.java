package com.hotel.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 객실 타입(RoomType) 및 객실 태그(RoomTag)에 대한 통합 보존 쿼터(Safety Stock Hold) 관리 정책
 */
public class QuotaPolicy {

    // [초기 기본 상수 정의 - 이후 언제든 관리자가 동적 변경 가능]
    public static final int DEFAULT_EXECUTIVE_DOUBLE_HOLD = 1;
    public static final int DEFAULT_SUPERIOR_TWIN_HOLD = 2;
    public static final int DEFAULT_RESIDENTIAL_DOUBLE_HOLD = 2;
    public static final int DEFAULT_TOKYO_TOWER_VIEW_HOLD = 2;
    public static final int DEFAULT_ACCESSIBLE_HOLD = 1;

    // 1. 객실 타입별 킵 수량
    private final Map<RoomType, Integer> roomTypeHoldQuotas = new ConcurrentHashMap<>();

    // 2. 태그별 킵 수량 (태그 코드를 키로 관리하여 향후 신규 등록 태그 100% 대응)
    private final Map<String, Integer> tagHoldQuotas = new ConcurrentHashMap<>();

    public QuotaPolicy() {
        initDefaultQuotas();
    }

    /**
     * 기본 상수로 초기 쿼터 구성
     */
    private void initDefaultQuotas() {
        // 객실 타입별 킵 초기화
        setTypeHoldQuota(RoomType.EXECUTIVE_DOUBLE, DEFAULT_EXECUTIVE_DOUBLE_HOLD);
        setTypeHoldQuota(RoomType.SUPERIOR_TWIN, DEFAULT_SUPERIOR_TWIN_HOLD);
        setTypeHoldQuota(RoomType.RESIDENTIAL_DOUBLE, DEFAULT_RESIDENTIAL_DOUBLE_HOLD);

        // 태그별 킵 초기화
        setTagHoldQuota(RoomTag.VIEW_TOKYO_TOWER.code(), DEFAULT_TOKYO_TOWER_VIEW_HOLD);
        setTagHoldQuota(RoomTag.ACCESSIBLE.code(), DEFAULT_ACCESSIBLE_HOLD);
    }

    // --- 타입별 쿼터 조작 API ---
    public void setTypeHoldQuota(RoomType type, int quota) {
        Objects.requireNonNull(type, "RoomType은 필수입니다.");
        roomTypeHoldQuotas.put(type, Math.max(0, quota));
    }

    public int getTypeHoldQuota(RoomType type) {
        if (type == null) return 0;
        return roomTypeHoldQuotas.getOrDefault(type, 0);
    }

    // --- 태그별 쿼터 조작 API ---
    public void setTagHoldQuota(String tagCode, int quota) {
        Objects.requireNonNull(tagCode, "태그 코드는 필수입니다.");
        tagHoldQuotas.put(tagCode.toUpperCase(), Math.max(0, quota));
    }

    public int getTagHoldQuota(String tagCode) {
        if (tagCode == null) return 0;
        return tagHoldQuotas.getOrDefault(tagCode.toUpperCase(), 0);
    }

    // 기존 TagQuotaPolicy 호환용 별칭
    public int getHoldQuota(String tagCode) {
        return getTagHoldQuota(tagCode);
    }

    public void setHoldQuota(String tagCode, int quota) {
        setTagHoldQuota(tagCode, quota);
    }

    public Map<RoomType, Integer> getAllTypeQuotas() {
        return Collections.unmodifiableMap(roomTypeHoldQuotas);
    }

    public Map<String, Integer> getAllTagQuotas() {
        return Collections.unmodifiableMap(tagHoldQuotas);
    }
}