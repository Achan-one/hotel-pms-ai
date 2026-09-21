package com.hotel.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * AI가 비정형 메모를 분석하여 ON/OFF 스위치를 켠 태그 선호도 VO
 */
public record TagPreference(
        Set<String> preferredTags, // 가산점 (+) 대상 태그
        Set<String> avoidTags      // 감점 (-) 대상 태그
) {
    public TagPreference {
        preferredTags = (preferredTags != null) ? Collections.unmodifiableSet(new HashSet<>(preferredTags)) : Set.of();
        avoidTags = (avoidTags != null) ? Collections.unmodifiableSet(new HashSet<>(avoidTags)) : Set.of();
    }

    public static TagPreference empty() {
        return new TagPreference(Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return preferredTags.isEmpty() && avoidTags.isEmpty();
    }

    public int getSwitchCount() {
        return preferredTags.size() + avoidTags.size();
    }
}