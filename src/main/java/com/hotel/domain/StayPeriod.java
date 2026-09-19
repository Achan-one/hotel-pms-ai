package com.hotel.domain;

import java.time.LocalDate;
import java.util.Objects;

public class StayPeriod {
    private final LocalDate checkInDate;
    private final LocalDate checkOutDate;

    public StayPeriod(LocalDate checkInDate, int stayNights) {
        this.checkInDate = Objects.requireNonNull(checkInDate, "checkInDate는 필수입니다.");
        if (stayNights < 1) {
            throw new IllegalArgumentException("숙박 일수는 최소 1박 이상이어야 합니다.");
        }
        this.checkOutDate = checkInDate.plusDays(stayNights);
    }

    public StayPeriod(LocalDate checkInDate, LocalDate checkOutDate) {
        this.checkInDate = Objects.requireNonNull(checkInDate, "checkInDate는 필수입니다.");
        this.checkOutDate = Objects.requireNonNull(checkOutDate, "checkOutDate는 필수입니다.");
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("체크아웃 날짜는 체크인 날짜 이후여야 합니다.");
        }
    }

    /**
     * 반개구간 [checkIn, checkOut) 기준 두 기간의 겹침 여부 판정
     */
    public boolean overlaps(StayPeriod other) {
        if (other == null) return false;
        // checkIn < other.checkOut && checkOut > other.checkIn
        return this.checkInDate.isBefore(other.checkOutDate) && this.checkOutDate.isAfter(other.checkInDate);
    }

    public boolean contains(LocalDate targetDate) {
        if (targetDate == null) return false;
        // [checkIn, checkOut) 포함 여부
        return !targetDate.isBefore(checkInDate) && targetDate.isBefore(checkOutDate);
    }

    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StayPeriod that)) return false;
        return Objects.equals(checkInDate, that.checkInDate) && Objects.equals(checkOutDate, that.checkOutDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkInDate, checkOutDate);
    }

    @Override
    public String toString() {
        return String.format("%s ~ %s", checkInDate, checkOutDate);
    }
}