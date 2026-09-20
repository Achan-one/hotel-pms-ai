package com.hotel.domain;

public enum ReservationStatus {

    PENDING("접수완료", "방 미배정 상태"),
    ASSIGNED("배정완료", "객실 호실 배정 확정"),
    DUE_IN("도착예정", "체크인 대기 / 오늘 도착 예정"),
    CHECKED_IN("체크인완료", "키 수령 및 현재 객실 투숙 중 (In-House)"),
    ROOM_CHANGED("룸체인지", "처음 배정된 객실과 다른 객실로 이동하여 투숙 중"),
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

    // 자동 배정 대상인지 (방이 없는 PENDING만 가능)
    public boolean isAssignable() {
        return this == PENDING;
    }

    // 실제로 물리적 객실을 점유하고 있는 상태인지 (CHECKED_IN, ROOM_CHANGED)
    public boolean isInHouse() {
        return this == CHECKED_IN || this == ROOM_CHANGED;
    }

    // 방 번호가 유효하게 부여되어 있는 상태인지
    public boolean hasAssignedRoom() {
        return this == ASSIGNED || this == DUE_IN || this == CHECKED_IN || this == ROOM_CHANGED;
    }
}