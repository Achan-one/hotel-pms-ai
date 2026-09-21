package com.hotel.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 특정 태그를 가진 객실의 최소 보존 수량(Safety Quota)을 관리하는 도메인 정책
 */
public class TagQuotaPolicy {

    // 태그 코드 -> 보존(Keep)할 객실 수량
    private final Map<String, Integer> quotaStore = new ConcurrentHashMap<>();

    public TagQuotaPolicy() {
        // 기본값: 빈 쿼터 (보존 수량 없음)
    }

    /**
     * 특정 태그에 대해 자동 배정 제외 및 현장용으로 킵해둘 수량 설정
     *
     * @param tagCode 태그 코드 (예: "VIEW_TOKYO_TOWER", "ACCESSIBLE")
     * @param keepCount 보존할 객실 수량
     */
    public void setHoldQuota(String tagCode, int keepCount) {
        if (tagCode == null || tagCode.isBlank()) return;
        quotaStore.put(tagCode.trim().toUpperCase(), Math.max(0, keepCount));
    }

    public int getHoldQuota(String tagCode) {
        if (tagCode == null) return 0;
        return quotaStore.getOrDefault(tagCode.trim().toUpperCase(), 0);
    }

    public void removeHoldQuota(String tagCode) {
        if (tagCode != null) {
            quotaStore.remove(tagCode.trim().toUpperCase());
        }
    }

    public Map<String, Integer> getAllQuotas() {
        return Map.copyOf(quotaStore);
    }
}