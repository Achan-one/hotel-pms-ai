package com.hotel.domain;

public enum ReservationStatus {

    PENDING("접수완료", "방 미배정 상태"),
    ASSIGNED("배정완료", "객실 호실 배정 확정"),
    CHECKED_IN("체크인 전", "오늘 체크인 예정"),
    STAYING("숙박중", "현재 숙박 중"),
    ROOM_CHANGED("룸체인지", "처음 체크인 한 객실과 다른 객실 숙박 중"), // <-- 언더스코어 적용
    CHECKED_OUT("퇴실완료", "체크아웃 완료"),
    CANCELLED("예약취소", "고객 또는 호텔에 의한 취소");


    private final String title;
    private final String description;

    ReservationStatus(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    // 자동 배정 대상인지 (방이 없는 PENDING만 가능)
    public boolean isAssignable() {
        return this == PENDING;
    }

    // 실제로 물리적 객실을 점유하고 있는 상태인지 (STAYING, ROOMCHANGED)
    public boolean isInHouse() {
        return this == STAYING || this == ROOM_CHANGED;
    }

    // 방 번호가 이미 부여되어 유효한 상태인지 (ASSIGNED, CHECKED_IN, STAYING, ROOMCHANGED)
    public boolean hasAssignedRoom() {
        return this == ASSIGNED || this == CHECKED_IN || this == STAYING || this == ROOM_CHANGED;
    }
}