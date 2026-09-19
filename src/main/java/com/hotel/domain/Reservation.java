package com.hotel.domain;

import java.time.LocalDate;
import java.util.Objects;

public class Reservation {
    private final String reservationId;
    private final String guestName;
    private final RoomType bookedRoomType;
    private final LocalDate checkInDate;        // 체크인 날짜
    private final int stayNights;               // 숙박 일수 (1박 이상)
    private final String rawRequestText;
    private final GuestPreference preference;

    private String assignedRoomNumber;

    // 기존 6개 인자 생성자 하위 호환 지원 (기존 코드는 체크인 날짜를 오늘로 설정)
    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       int stayNights, String rawRequestText, GuestPreference preference) {
        this(reservationId, guestName, bookedRoomType, LocalDate.now(), stayNights, rawRequestText, preference);
    }

    // 날짜를 명시하는 생성자 (null이 들어오면 null 그대로 유지하여 Validator가 감지할 수 있도록 함)
    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       LocalDate checkInDate, int stayNights, String rawRequestText, GuestPreference preference) {
        this.reservationId = Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        this.guestName = Objects.requireNonNull(guestName, "투숙객 이름은 필수입니다.");
        this.bookedRoomType = Objects.requireNonNull(bookedRoomType, "예약 객실 타입은 필수입니다.");
        this.checkInDate = checkInDate; // <-- null이면 null 그대로 대입!

        if (stayNights < 1) {
            throw new IllegalArgumentException("숙박 일수는 최소 1박 이상이어야 합니다.");
        }
        this.stayNights = stayNights;

        this.rawRequestText = (rawRequestText != null && !rawRequestText.isBlank()) ? rawRequestText.trim() : null;
        this.preference = (preference != null) ? preference : GuestPreference.empty();
        this.assignedRoomNumber = null;
    }

    public void assignRoom(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new IllegalArgumentException("배정할 객실 번호가 올바르지 않습니다.");
        }
        this.assignedRoomNumber = roomNumber.trim();
    }

    public void cancelAssignment() {
        this.assignedRoomNumber = null;
    }

    public boolean isAssigned() {
        return this.assignedRoomNumber != null;
    }

    public Reservation withPreference(GuestPreference newPreference) {
        Reservation cloned = new Reservation(
                this.reservationId,
                this.guestName,
                this.bookedRoomType,
                this.checkInDate,
                this.stayNights,
                this.rawRequestText,
                newPreference
        );
        if (this.isAssigned()) {
            cloned.assignRoom(this.assignedRoomNumber);
        }
        return cloned;
    }

    public LocalDate getCheckOutDate() {
        if (this.checkInDate == null) {
            return null;
        }
        return this.checkInDate.plusDays(this.stayNights);
    }

    // Getters
    public String getReservationId() { return reservationId; }
    public String getGuestName() { return guestName; }
    public RoomType getBookedRoomType() { return bookedRoomType; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public int getStayNights() { return stayNights; }
    public String getRawRequestText() { return rawRequestText; }
    public GuestPreference getPreference() { return preference; }
    public String getAssignedRoomNumber() { return assignedRoomNumber; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return Objects.equals(reservationId, that.reservationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId);
    }

    @Override
    public String toString() {
        return String.format("[%s | %s | %s | %s~%s (%d박) | %s | 배정:%s]",
                reservationId,
                guestName,
                bookedRoomType.getDescription(),
                checkInDate,
                getCheckOutDate(),
                stayNights,
                preference,
                isAssigned() ? (assignedRoomNumber + "호") : "미배정"
        );
    }
}