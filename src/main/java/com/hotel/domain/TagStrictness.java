package com.hotel.domain;

/**
 * 태그의 충족 필수 여부 (엄격도)
 */
public enum TagStrictness {
    HARD("필수 제약 (Hard)", "미충족 시 프론트 데스크 경고 알림 대상"),
    SOFT("취향 선호 (Soft)", "미충족 시 차선책 객실 자동 배정");

    private final String title;
    private final String description;

    TagStrictness(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public boolean isHard() {
        return this == HARD;
    }
}