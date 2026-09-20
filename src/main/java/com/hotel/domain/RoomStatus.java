package com.hotel.domain;

public enum RoomStatus {
    VACANT("공실", "배정 가능"),
    OCCUPIED("재실", "기존 투숙 중"),
    ASSIGNED("배정완료", "신규 예약 배정 확정"),
    BLOCKED("점검중", "일시적 점검/홀딩"),
    OUT("아웃", "체크아웃 완료 (청소 대기)"),
    CLEANING("청소중", "하우스키핑 청소 진행 중"),
    BREAK("고장", "시설 고장 및 판매 중지 (OOO)");

    private final String title;
    private final String description;

    RoomStatus(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public boolean isAssignable() {
        return this == VACANT;
    }

    public boolean isOutOfService() {
        return this == BLOCKED || this == BREAK;
    }
}