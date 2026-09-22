package com.hotel.domain;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Room {
    private final String roomNumber;
    private final int floor;
    private final RoomType roomType;
    private final boolean nearElevator;
    private final boolean cornerRoom;

    private final Set<String> tags;
    private RoomStatus status;
    private boolean assigned;
    private final List<StayPeriod> bookedPeriods;
    private final ReentrantLock lock = new ReentrantLock(true);

    public Room(String roomNumber, int floor, RoomType roomType, boolean nearElevator, boolean cornerRoom) {
        this.roomNumber = roomNumber;
        this.floor = floor;
        this.roomType = roomType;
        this.nearElevator = nearElevator;
        this.cornerRoom = cornerRoom;
        this.status = RoomStatus.VACANT;
        this.assigned = false;
        this.bookedPeriods = new ArrayList<>();

        this.tags = new HashSet<>();
        initDefaultTags();
    }

    private void initDefaultTags() {
        if (this.floor >= 10) tags.add(RoomTag.HIGH_FLOOR.code());
        if (this.floor <= 6) tags.add(RoomTag.LOW_FLOOR.code());
        if (this.nearElevator) tags.add(RoomTag.NEAR_ELEVATOR.code());
        else tags.add(RoomTag.AWAY_FROM_ELEVATOR.code());

        if (this.cornerRoom) tags.add(RoomTag.CORNER_ROOM.code());
        if (!this.nearElevator && this.cornerRoom) tags.add(RoomTag.QUIET_ZONE.code());
    }

    public void addTag(String tagCode) {
        lock.lock();
        try {
            if (tagCode != null && !tagCode.isBlank()) {
                this.tags.add(tagCode.trim().toUpperCase());
            }
        } finally {
            lock.unlock();
        }
    }

    public void removeTag(String tagCode) {
        lock.lock();
        try {
            if (tagCode != null) {
                this.tags.remove(tagCode.trim().toUpperCase());
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean hasTag(String tagCode) {
        lock.lock();
        try {
            return tagCode != null && this.tags.contains(tagCode.trim().toUpperCase());
        } finally {
            lock.unlock();
        }
    }

    public Set<String> getTags() {
        lock.lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(tags));
        } finally {
            lock.unlock();
        }
    }

    public boolean isAvailable(StayPeriod period) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public boolean tryBookPeriod(StayPeriod period) {
        lock.lock();
        try {
            if (period == null || !isAvailable(period)) {
                return false;
            }
            this.bookedPeriods.add(period);
            this.assigned = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void bookPeriod(StayPeriod period) {
        lock.lock();
        try {
            if (!tryBookPeriod(period)) {
                throw new IllegalStateException("해당 기간에 배정할 수 없는 객실입니다: " + roomNumber + " (" + period + ")");
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isOccupiedOn(LocalDate targetDate) {
        lock.lock();
        try {
            if (targetDate == null) return false;
            if (this.assigned && bookedPeriods.isEmpty()) {
                return true;
            }
            return bookedPeriods.stream().anyMatch(booked -> booked.contains(targetDate));
        } finally {
            lock.unlock();
        }
    }

    public void assign() {
        lock.lock();
        try {
            if (this.assigned) return;
            this.assigned = true;
        } finally {
            lock.unlock();
        }
    }

    public void release() {
        lock.lock();
        try {
            if (!this.assigned) return;
            this.assigned = false;
            this.bookedPeriods.clear();
            if (this.status.canTransitionTo(RoomStatus.VACANT)) {
                this.status = RoomStatus.VACANT;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelPeriod(StayPeriod period) {
        lock.lock();
        try {
            if (period == null) return false;
            boolean removed = this.bookedPeriods.remove(period);
            if (this.bookedPeriods.isEmpty()) {
                this.assigned = false;
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 특정 고객의 투숙 기간(targetPeriod)을 타겟팅하여 moveDate 이후 잔여 일정을 단축/회수합니다.
     * 동일 객실에 이후 날짜로 예약된 다른 고객의 스케줄은 절대 훼손되지 않습니다.
     */
    public void truncatePeriodFrom(StayPeriod targetPeriod, LocalDate moveDate) {
        if (moveDate == null) return;
        if (targetPeriod == null) {
            truncatePeriodFrom(moveDate);
            return;
        }

        lock.lock();
        try {
            List<StayPeriod> updated = new ArrayList<>();
            for (StayPeriod p : this.bookedPeriods) {
                if (!p.overlaps(targetPeriod)) {
                    // 대상 예약과 무관한 다른 손님의 스케줄은 온전히 보존
                    updated.add(p);
                } else {
                    // 대상 예약과 겹치는 스케줄만 축소
                    if (!p.getCheckOutDate().isAfter(moveDate)) {
                        updated.add(p);
                    } else if (moveDate.isAfter(p.getCheckInDate()) && moveDate.isBefore(p.getCheckOutDate())) {
                        updated.add(new StayPeriod(p.getCheckInDate(), moveDate));
                    }
                }
            }

            this.bookedPeriods.clear();
            this.bookedPeriods.addAll(updated);
            if (this.bookedPeriods.isEmpty()) {
                this.assigned = false;
            }
        } finally {
            lock.unlock();
        }
    }

    public void truncatePeriodFrom(LocalDate moveDate) {
        if (moveDate == null) return;

        lock.lock();
        try {
            List<StayPeriod> updated = new ArrayList<>();
            for (StayPeriod p : this.bookedPeriods) {
                if (!p.getCheckOutDate().isAfter(moveDate)) {
                    updated.add(p);
                } else if (moveDate.isAfter(p.getCheckInDate()) && moveDate.isBefore(p.getCheckOutDate())) {
                    updated.add(new StayPeriod(p.getCheckInDate(), moveDate));
                }
            }

            this.bookedPeriods.clear();
            this.bookedPeriods.addAll(updated);
            if (this.bookedPeriods.isEmpty()) {
                this.assigned = false;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean hasScheduleConflict(StayPeriod period) {
        return !isAvailable(period);
    }

    public String getRoomNumber() { return roomNumber; }
    public int getFloor() { return floor; }
    public RoomType getRoomType() { return roomType; }
    public boolean isNearElevator() { return nearElevator; }
    public boolean isCorner() { return cornerRoom; }

    public boolean isAssigned() {
        lock.lock();
        try {
            return this.assigned || !this.bookedPeriods.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public List<StayPeriod> getBookedPeriods() {
        lock.lock();
        try {
            return List.copyOf(bookedPeriods);
        } finally {
            lock.unlock();
        }
    }

    public RoomStatus getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    public void setStatus(RoomStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus는 필수입니다.");
        lock.lock();
        try {
            if (!this.status.canTransitionTo(newStatus)) {
                throw new IllegalStateException(String.format(
                        "[%s호] 허용되지 않는 상태 전이입니다: %s -> %s",
                        roomNumber, this.status.getTitle(), newStatus.getTitle()
                ));
            }
            this.status = newStatus;
        } finally {
            lock.unlock();
        }
    }

    public void markCheckOut() {
        setStatus(RoomStatus.OUT);
    }

    public void startCleaning() {
        setStatus(RoomStatus.CLEANING);
    }

    public void finishCleaning() {
        setStatus(RoomStatus.VACANT);
    }

    public void markOutOfService(boolean isBreak) {
        setStatus(isBreak ? RoomStatus.BREAK : RoomStatus.BLOCKED);
    }

    @Override
    public String toString() {
        return String.format("[%s호 | %2d층 | %-10s | 상태:%s | 태그:%s | %s]",
                roomNumber, floor, roomType.getDescription(),
                getStatus().getTitle(),
                getTags(),
                isAssigned() ? "배정완료" : "공실"
        );
    }
}