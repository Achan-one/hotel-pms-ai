package com.hotel.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Room {
    private final String roomNumber;
    private final int floor;
    private final RoomType roomType;
    private final boolean nearElevator;
    private final boolean cornerRoom;

    // [신규] 태그 지향 아키텍처: 동적 태그 컬렉션
    private final Set<String> tags;

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

        // [신규] 태그 세트 초기화 및 기존 물리 속성 기반 기본 태그 자동 세팅
        this.tags = new HashSet<>();
        initDefaultTags();
    }

    /**
     * 기존 물리 속성을 태그로 매핑하여 기본 탑재
     */
    private void initDefaultTags() {
        if (this.floor >= 10) tags.add(RoomTag.HIGH_FLOOR.code());
        if (this.floor <= 6) tags.add(RoomTag.LOW_FLOOR.code());
        if (this.nearElevator) tags.add(RoomTag.NEAR_ELEVATOR.code());
        else tags.add(RoomTag.AWAY_FROM_ELEVATOR.code());

        if (this.cornerRoom) tags.add(RoomTag.CORNER_ROOM.code());
        if (!this.nearElevator && this.cornerRoom) tags.add(RoomTag.QUIET_ZONE.code());
    }

    // ==========================================
    // [신규] 동적 태그 관리 API
    // ==========================================
    public void addTag(String tagCode) {
        if (tagCode != null && !tagCode.isBlank()) {
            this.tags.add(tagCode.trim().toUpperCase());
        }
    }

    public void removeTag(String tagCode) {
        if (tagCode != null) {
            this.tags.remove(tagCode.trim().toUpperCase());
        }
    }

    public boolean hasTag(String tagCode) {
        return tagCode != null && this.tags.contains(tagCode.trim().toUpperCase());
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /**
     * 특정 투숙 기간에 해당 객실이 배정 가능한지 확인
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

    public void truncatePeriodFrom(LocalDate moveDate) {
        if (moveDate == null) return;

        List<StayPeriod> updated = new ArrayList<>();
        for (StayPeriod p : this.bookedPeriods) {
            if (!moveDate.isBefore(p.getCheckInDate()) && moveDate.isBefore(p.getCheckOutDate())) {
                if (moveDate.isAfter(p.getCheckInDate())) {
                    updated.add(new StayPeriod(p.getCheckInDate(), moveDate));
                }
            } else if (!p.getCheckOutDate().isAfter(moveDate)) {
                updated.add(p);
            }
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
        return String.format("[%s호 | %2d층 | %-10s | 상태:%s | 태그:%s | %s]",
                roomNumber, floor, roomType.getDescription(),
                status.getTitle(),
                tags,
                isAssigned() ? "배정완료" : "공실"
        );
    }
}