package com.hotel.domain;

import java.time.LocalDate;
import java.util.Objects;

public class Reservation {
    private final String reservationId;
    private final String guestName;
    private final RoomType bookedRoomType;
    private final LocalDate checkInDate;
    private final int stayNights;
    private final String rawRequestText;
    private final GuestPreference preference;

    private String assignedRoomNumber;
    private ReservationStatus status;

    // 하위 호환용 6개 인자 생성자 (당일 체크인 기준)
    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       int stayNights, String rawRequestText, GuestPreference preference) {
        this(reservationId, guestName, bookedRoomType, LocalDate.now(), stayNights, rawRequestText, preference);
    }

    // 날짜 명시 생성자
    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       LocalDate checkInDate, int stayNights, String rawRequestText, GuestPreference preference) {
        this.reservationId = Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        this.guestName = Objects.requireNonNull(guestName, "투숙객 이름은 필수입니다.");
        this.bookedRoomType = Objects.requireNonNull(bookedRoomType, "예약 객실 타입은 필수입니다.");
        this.checkInDate = checkInDate;

        if (stayNights < 1) {
            throw new IllegalArgumentException("숙박 일수는 최소 1박 이상이어야 합니다.");
        }
        this.stayNights = stayNights;

        this.rawRequestText = (rawRequestText != null && !rawRequestText.isBlank()) ? rawRequestText.trim() : null;
        this.preference = (preference != null) ? preference : GuestPreference.empty();
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.PENDING; // 최초 인입 시 '접수완료(방 미배정)'
    }

    // ==========================================
    // 예약 상태 전이 (State Transition) 도메인 로직
    // ==========================================

    /**
     * 객실 자동/수동 배정 확정
     */
    public void assignRoom(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new IllegalArgumentException("배정할 객실 번호가 올바르지 않습니다.");
        }
        this.assignedRoomNumber = roomNumber.trim();
        this.status = ReservationStatus.ASSIGNED;
    }

    /**
     * 배정 취소 (다시 미배정 접수 상태로 복구)
     */
    public void cancelAssignment() {
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.PENDING;
    }

    /**
     * 당일 도착 예정 확정 (배정 완료 상태 -> 체크인 전 대기 상태)
     */
    public void markReadyForCheckIn() {
        if (this.assignedRoomNumber == null) {
            throw new IllegalStateException("객실이 배정되지 않은 상태에서는 체크인 전 단계로 변경할 수 없습니다.");
        }
        this.status = ReservationStatus.CHECKED_IN;
    }

    /**
     * 실제 프론트 도착 및 키 카드 수령 (숙박중 상태로 전환)
     */
    public void startStaying() {
        if (this.assignedRoomNumber == null) {
            throw new IllegalStateException("객실이 배정되지 않은 예약은 입실(STAYING)할 수 없습니다.");
        }
        this.status = ReservationStatus.STAYING;
    }

    /**
     * 룸 체인지 실행 (신규 객실 번호 갱신 및 상태 변경)
     */
    public void changeRoom(String newRoomNumber) {
        if (newRoomNumber == null || newRoomNumber.isBlank()) {
            throw new IllegalArgumentException("이동할 신규 객실 번호가 올바르지 않습니다.");
        }
        this.assignedRoomNumber = newRoomNumber.trim();
        this.status = ReservationStatus.ROOM_CHANGED;
    }

    /**
     * 퇴실 처리 (체크아웃 완료)
     */
    public void checkOut() {
        if (!this.status.isInHouse()) {
            throw new IllegalStateException("현재 숙박 중(STAYING 또는 ROOM_CHANGED)인 고객만 체크아웃할 수 있습니다.");
        }
        this.status = ReservationStatus.CHECKED_OUT;
    }

    /**
     * 예약 자체 취소
     */
    public void cancelReservation() {
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * 유효한 객실이 부여되어 있는지 여부 (배정, 체크인 전, 숙박중, 룸체인지 모두 포함)
     */
    public boolean isAssigned() {
        return this.assignedRoomNumber != null && this.status.hasAssignedRoom();
    }

    /**
     * 불변 선호도 주입 복제 (AI 파싱 결과 반영 시 상태 유지)
     */
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
        cloned.status = this.status;
        if (this.assignedRoomNumber != null) {
            cloned.assignedRoomNumber = this.assignedRoomNumber;
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
    public ReservationStatus getStatus() { return status; }

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
        return String.format("[%s | %s | %s | %s~%s (%d박) | 상태:%s | 배정:%s]",
                reservationId,
                guestName,
                bookedRoomType.getDescription(),
                checkInDate,
                getCheckOutDate(),
                stayNights,
                status.getTitle(),
                isAssigned() ? (assignedRoomNumber + "호") : "미배정"
        );
    }
}