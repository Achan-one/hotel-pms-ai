package com.hotel.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Room {
    private final String roomNumber;
    private final int floor;
    private final RoomType roomType;
    private final boolean nearElevator;
    private final boolean cornerRoom;

    // 기존 단일 스냅샷 호환용 플래그
    private boolean assigned;

    // 날짜 기반 스케줄 컬렉션 [checkIn, checkOut)
    private final List<StayPeriod> bookedPeriods;

    public Room(String roomNumber, int floor, RoomType roomType, boolean nearElevator, boolean cornerRoom) {
        this.roomNumber = roomNumber;
        this.floor = floor;
        this.roomType = roomType;
        this.nearElevator = nearElevator;
        this.cornerRoom = cornerRoom;
        this.assigned = false;
        this.bookedPeriods = new ArrayList<>();
    }

    /**
     * 특정 투숙 기간에 해당 객실이 비어있는지 확인
     */
    public boolean isAvailable(StayPeriod period) {
        if (period == null) {
            return true;
        }

        // 날짜 없이 room.assign()만 호출된 기존 재실/고장 객실 방어
        if (this.assigned && bookedPeriods.isEmpty()) {
            return false;
        }

        // 해당 기간과 겹치는 예약이 없으면 배정 가능! (당일 체크아웃/체크인 회전 지원)
        return bookedPeriods.stream().noneMatch(booked -> booked.overlaps(period));
    }

    /**
     * 날짜 구간을 지정하여 객실 예약 확정
     */
    public void bookPeriod(StayPeriod period) {
        if (!isAvailable(period)) {
            throw new IllegalStateException("해당 기간에 이미 예약이 존재하는 객실입니다: " + roomNumber + " (" + period + ")");
        }
        this.bookedPeriods.add(period);
        this.assigned = true;
    }

    /**
     * 특정 날짜에 손님이 묵고 있는지 확인 (뷰어 렌더링용)
     */
    public boolean isOccupiedOn(LocalDate targetDate) {
        if (this.assigned && bookedPeriods.isEmpty()) {
            return true; // simulateExistingCheckIns 레거시 플래그 호환
        }
        return bookedPeriods.stream().anyMatch(booked -> booked.contains(targetDate));
    }

    public void assign() {
        if (this.assigned) {
            System.out.println("이미 배정된 객실입니다: " + roomNumber);
            return;
        }
        this.assigned = true;
    }

    public void release() {
        if (!this.assigned) {
            System.out.println("Assign이 아직 이루어지지 않은 객실입니다.");
            return;
        }
        this.assigned = false;
        this.bookedPeriods.clear();
    }

    // Getters
    public String getRoomNumber() { return roomNumber; }
    public int getFloor() { return floor; }
    public RoomType getRoomType() { return roomType; }
    public boolean isNearElevator() { return nearElevator; }
    public boolean isCorner() { return cornerRoom; }
    public boolean isAssigned() { return this.assigned || !this.bookedPeriods.isEmpty(); }
    public List<StayPeriod> getBookedPeriods() { return List.copyOf(bookedPeriods); }

    @Override
    public String toString() {
        return String.format("[%s호 | %2d층 | %-10s | 엘베:%s | 코너:%s | %s]",
                roomNumber, floor, roomType.getDescription(),
                nearElevator ? "O" : "X",
                cornerRoom ? "O" : "X",
                isAssigned() ? "배정완료" : "공실"
        );
    }
}