package com.hotel.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Room {
    private final String roomNumber;
    private final int floor;
    private final RoomType roomType;
    private final boolean nearElevator;
    private final boolean cornerRoom;

    // 하우스키핑 및 운영 룸 랙 상태 (기본값: VACANT)
    private RoomStatus status;

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
        this.status = RoomStatus.VACANT;
        this.assigned = false;
        this.bookedPeriods = new ArrayList<>();
    }

    /**
     * 특정 투숙 기간에 해당 객실이 배정 가능한지 확인
     * 1. 룸 랙 상태가 고장(BREAK)이나 점검(BLOCKED) 등 판매 불가 상태면 즉시 거절
     * 2. 해당 날짜 구간에 겹치는 스케줄이 없어야 통과
     */
    public boolean isAvailable(StayPeriod period) {
        if (this.status.isOutOfService()) {
            return false;
        }

        if (period == null) {
            return true;
        }

        if (this.assigned && bookedPeriods.isEmpty()) {
            return false;
        }

        return bookedPeriods.stream().noneMatch(booked -> booked.overlaps(period));
    }

    public void bookPeriod(StayPeriod period) {
        if (!isAvailable(period)) {
            throw new IllegalStateException("해당 기간에 배정할 수 없는 객실입니다: " + roomNumber + " (" + period + ")");
        }
        this.bookedPeriods.add(period);
        this.assigned = true;
    }

    public boolean isOccupiedOn(LocalDate targetDate) {
        if (this.assigned && bookedPeriods.isEmpty()) {
            return true;
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
        this.status = RoomStatus.VACANT;
    }

    /**
     * 잔여 스케줄 부분 반납
     */
    public boolean cancelPeriod(StayPeriod period) {
        if (period == null) {
            return false;
        }
        boolean removed = this.bookedPeriods.remove(period);
        if (this.bookedPeriods.isEmpty()) {
            this.assigned = false;
        }
        return removed;
    }

    /**
     * 특정 이동 일자(moveDate) 이후의 잔여 스케줄을 잘라내어 반납합니다.
     * - 당일 체크인 0박 룸 무브: moveDate == checkInDate -> 기존 방 스케줄 완전 회수
     * - 연박 중 룸 무브: checkInDate < moveDate < checkOutDate -> 과거 투숙 구간만 보존
     * - 조기 퇴실: moveDate 기준 이후 스케줄 즉시 반납
     */
    public void truncatePeriodFrom(LocalDate moveDate) {
        if (moveDate == null) return;

        List<StayPeriod> updated = new ArrayList<>();
        for (StayPeriod p : this.bookedPeriods) {
            // 1. 이동 일자가 투숙 기간 중간에 걸쳐 있는 경우: 과거 구간만 보존
            if (!moveDate.isBefore(p.getCheckInDate()) && moveDate.isBefore(p.getCheckOutDate())) {
                if (moveDate.isAfter(p.getCheckInDate())) {
                    updated.add(new StayPeriod(p.getCheckInDate(), moveDate));
                }
                // moveDate.equals(p.getCheckInDate())인 경우 (0박 당일 이동):
                // 과거 투숙이 없으므로 updated에 추가하지 않고 완전히 비움
            }
            // 2. 이동 일자보다 완전히 이전인 과거 투숙은 그대로 유지
            else if (!p.getCheckOutDate().isAfter(moveDate)) {
                updated.add(p);
            }
            // 3. 이동 일자 이후의 미래 구간은 리스트에서 자동 탈락(반납)
        }

        this.bookedPeriods.clear();
        this.bookedPeriods.addAll(updated);
        if (this.bookedPeriods.isEmpty()) {
            this.assigned = false;
        }
    }

    public boolean hasScheduleConflict(StayPeriod period) {
        return !isAvailable(period);
    }

    // Getters & Setters
    public String getRoomNumber() { return roomNumber; }
    public int getFloor() { return floor; }
    public RoomType getRoomType() { return roomType; }
    public boolean isNearElevator() { return nearElevator; }
    public boolean isCorner() { return cornerRoom; }
    public boolean isAssigned() { return this.assigned || !this.bookedPeriods.isEmpty(); }
    public List<StayPeriod> getBookedPeriods() { return List.copyOf(bookedPeriods); }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = Objects.requireNonNull(status); }

    @Override
    public String toString() {
        return String.format("[%s호 | %2d층 | %-10s | 상태:%s | 엘베:%s | 코너:%s | %s]",
                roomNumber, floor, roomType.getDescription(),
                status.getTitle(),
                nearElevator ? "O" : "X",
                cornerRoom ? "O" : "X",
                isAssigned() ? "배정완료" : "공실"
        );
    }
}