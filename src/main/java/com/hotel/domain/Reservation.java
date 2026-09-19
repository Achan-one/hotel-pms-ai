package com.hotel.domain;
import java.util.Objects;

public class Reservation {
    private final String reservationId;
    private final String guestName;
    private final RoomType bookedRoomType;

    private final String rawRequestText;
    private final GuestPreference preference;

    private String assignedRoomNumber;

    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       String rawRequestText, GuestPreference preference) {
        this.reservationId = Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        this.guestName = Objects.requireNonNull(guestName, "투숙객 이름은 필수입니다.");
        this.bookedRoomType = Objects.requireNonNull(bookedRoomType, "예약 객실 타입은 필수입니다.");
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

    // Getters
    public String getReservationId() { return reservationId; }
    public String getGuestName() { return guestName; }
    public RoomType getBookedRoomType() { return bookedRoomType; }
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
        return String.format("[%s | %s | %s | %s | 배정:%s]",
                reservationId,
                guestName,
                bookedRoomType.getDescription(),
                preference,
                isAssigned() ? (assignedRoomNumber + "호") : "미배정"
        );
    }
}