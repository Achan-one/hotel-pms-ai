package com.hotel.domain;

public enum RoomStatus {
    VACANT("공실", "배정 및 즉시 입실 가능"),
    OCCUPIED("재실", "투숙객 체류 중"),
    ASSIGNED("배정완료", "신규 예약 배정 확정"),
    BLOCKED("점검중", "일시적 점검/홀딩"),
    OUT("아웃", "체크아웃 완료 (더티/청소 대기)"),
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

    /**
     * 실무 상태 전이 가능 여부 검증
     * - VACANT: 입실(OCCUPIED/ASSIGNED), 점검(BLOCKED/BREAK), 또는 재청소/쇼룸퇴실(OUT) 허용
     * - OCCUPIED: 반드시 OUT(체크아웃)을 거쳐야만 청소 단계로 진입 가능 (직접 VACANT 건너뛰기 차단)
     * - OUT: CLEANING 시작 또는 점검(BLOCKED/BREAK)
     * - CLEANING: 청소 완료 시 VACANT 환원, 미흡 시 OUT 재전이, 고장 발견 시 BREAK
     * - BLOCKED, BREAK: 정비 완료 후 청소/점검(OUT/CLEANING) 또는 즉시 점검완료(VACANT) 복구
     */
    public boolean canTransitionTo(RoomStatus target) {
        if (this == target) return true;

        return switch (this) {
            case VACANT -> target == ASSIGNED || target == OCCUPIED || target == OUT || target == BLOCKED || target == BREAK;
            case ASSIGNED -> target == OCCUPIED || target == VACANT || target == OUT || target == BLOCKED || target == BREAK;
            case OCCUPIED -> target == OUT; // 투숙 중인 객실은 무조건 퇴실(OUT)만 가능 (VACANT 직접 건너뛰기 방어 핵심)
            case OUT -> target == CLEANING || target == BLOCKED || target == BREAK;
            case CLEANING -> target == VACANT || target == OUT || target == BREAK;
            case BLOCKED, BREAK -> target == OUT || target == CLEANING || target == VACANT;
        };
    }
}