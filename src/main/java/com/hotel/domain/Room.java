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

    // 동적 태그 컬렉션
    private final Set<String> tags;

    // 하우스키핑 및 운영 룸 랙 상태 (기본값: VACANT)
    private RoomStatus status;

    // 기존 단일 스냅샷 호환용 플래그
    private boolean assigned;

    // 날짜 기반 스케줄 컬렉션 [checkIn, checkOut)
    private final List<StayPeriod> bookedPeriods;

    // 동시성 제어 및 더블 부킹 방어를 위한 인스턴스 단위 재진입 락 (공정성 보장)
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

    /**
     * 특정 투숙 기간에 해당 객실이 배정 가능한지 확인 (동시성 보호)
     */
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

    /**
     * [원자적 점유 메서드 - 핵심]
     * Check-Then-Act 구간을 단일 락 안에서 실행하여 더블 부킹을 원천 차단합니다.
     *
     * @param period 점유할 투숙 기간
     * @return 배정 성공 시 true, 다른 스레드가 먼저 점유했거나 점검 중인 경우 false
     */
    public boolean tryBookPeriod(StayPeriod period) {
        lock.lock();
        try {
            if (!isAvailable(period)) {
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
            if (this.assigned) {
                return;
            }
            this.assigned = true;
        } finally {
            lock.unlock();
        }
    }

    public void release() {
        lock.lock();
        try {
            if (!this.assigned) {
                return;
            }
            this.assigned = false;
            this.bookedPeriods.clear();
            this.status = RoomStatus.VACANT;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelPeriod(StayPeriod period) {
        lock.lock();
        try {
            if (period == null) {
                return false;
            }
            boolean removed = this.bookedPeriods.remove(period);
            if (this.bookedPeriods.isEmpty()) {
                this.assigned = false;
            }
            return removed;
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
        } finally {
            lock.unlock();
        }
    }

    public boolean hasScheduleConflict(StayPeriod period) {
        return !isAvailable(period);
    }

    // Getters & Setters (동시성 보호)
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

    /**
     * 상태 전이 비즈니스 규칙 검증 및 반영 (동시성 락 보호)
     */
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

    // --- 하우스키핑 실무 캡슐화 전용 메서드 ---

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