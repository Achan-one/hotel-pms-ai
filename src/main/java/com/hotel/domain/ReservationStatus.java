package com.hotel.domain;

public enum ReservationStatus {

    PENDING("접수완료", "방 미배정 상태"),
    ASSIGNED("배정완료", "객실 호실 배정 확정"),
    DUE_IN("도착예정", "체크인 대기 / 오늘 도착 예정"),
    CHECKED_IN("투숙중", "키 수령 및 현재 객실 투숙 중 (In-House)"),
    CHECKED_OUT("퇴실완료", "체크아웃 정산 및 퇴실 완료"),
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

    public boolean isAssignable() {
        return this == PENDING;
    }

    public boolean isInHouse() {
        return this == CHECKED_IN;
    }

    public boolean hasAssignedRoom() {
        return this == ASSIGNED || this == DUE_IN || this == CHECKED_IN;
    }
}