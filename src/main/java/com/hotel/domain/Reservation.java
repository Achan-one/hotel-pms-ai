package com.hotel.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class Reservation {
    // 1. 기본 식별 및 객실 기본 정보
    private final String reservationId;
    private final String guestName;
    private final RoomType bookedRoomType;
    private final LocalDate checkInDate;
    private final int stayNights;
    private final int guestCount;
    private final String rawRequestText;
    private final GuestPreference preference;

    // 태그 지향 아키텍처: AI가 스위치를 켠 동적 태그 선호도
    private final TagPreference tagPreference;

    // 2. 호텔 실무 확장 필드 (채널, 조식, 정산, 도착/출발 시간)
    private final BookingChannelInfo channelInfo;
    private final BreakfastOption breakfastOption;
    private final PaymentLedger paymentLedger;
    private LocalTime estimatedArrivalTime; // 예상 도착 시각 (ETA)
    private LocalTime lateCheckOutTime;     // 레이트 체크아웃 확정 시각

    // 3. 동적 상태 필드
    private String assignedRoomNumber;
    private String previousRoomNumber;      // 룸 체인지 이전 호실 이력 추적 필드
    private LocalDate actualCheckOutDate;   // 실제 체크아웃 발생일자 (조기 퇴실 및 연장 감사용)
    private ReservationStatus status;

    // ==========================================
    // 생성자 (태그 선호도 포함 전체 인자 마스터 생성자)
    // ==========================================
    public Reservation(String reservationId,
                       String guestName,
                       RoomType bookedRoomType,
                       LocalDate checkInDate,
                       int stayNights,
                       int guestCount,
                       String rawRequestText,
                       GuestPreference preference,
                       TagPreference tagPreference,
                       BookingChannelInfo channelInfo,
                       BreakfastOption breakfastOption,
                       PaymentLedger paymentLedger,
                       LocalTime estimatedArrivalTime) {

        this.reservationId = Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        this.guestName = Objects.requireNonNull(guestName, "투숙객 이름은 필수입니다.");
        this.bookedRoomType = Objects.requireNonNull(bookedRoomType, "예약 객실 타입은 필수입니다.");
        this.checkInDate = Objects.requireNonNull(checkInDate, "체크인 날짜는 필수입니다.");

        if (stayNights < 1) {
            throw new IllegalArgumentException("숙박 일수는 최소 1박 이상이어야 합니다.");
        }
        this.stayNights = stayNights;
        this.guestCount = Math.max(1, guestCount);

        this.rawRequestText = (rawRequestText != null && !rawRequestText.isBlank()) ? rawRequestText.trim() : null;
        this.preference = (preference != null) ? preference : GuestPreference.empty();
        this.tagPreference = (tagPreference != null) ? tagPreference : TagPreference.empty();

        // 실무 VO 기본값 세팅
        this.channelInfo = (channelInfo != null) ? channelInfo : BookingChannelInfo.direct(reservationId);
        this.breakfastOption = (breakfastOption != null) ? breakfastOption : BreakfastOption.none();
        this.paymentLedger = (paymentLedger != null) ? paymentLedger : new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 0);
        this.estimatedArrivalTime = (estimatedArrivalTime != null) ? estimatedArrivalTime : LocalTime.of(15, 0);
        this.lateCheckOutTime = null;

        this.assignedRoomNumber = null;
        this.previousRoomNumber = null;
        this.actualCheckOutDate = null;
        this.status = ReservationStatus.PENDING;
    }

    // ==========================================
    // 편의 오버로딩 생성자 (레거시 및 기존 코드 100% 호환)
    // ==========================================

    public Reservation(String reservationId,
                       String guestName,
                       RoomType bookedRoomType,
                       LocalDate checkInDate,
                       int stayNights,
                       int guestCount,
                       String rawRequestText,
                       GuestPreference preference,
                       BookingChannelInfo channelInfo,
                       BreakfastOption breakfastOption,
                       PaymentLedger paymentLedger,
                       LocalTime estimatedArrivalTime) {
        this(reservationId, guestName, bookedRoomType, checkInDate, stayNights, guestCount,
                rawRequestText, preference, TagPreference.empty(), channelInfo, breakfastOption, paymentLedger, estimatedArrivalTime);
    }

    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       int stayNights, String rawRequestText, GuestPreference preference) {
        this(reservationId, guestName, bookedRoomType, LocalDate.now(), stayNights, 1,
                rawRequestText, preference, TagPreference.empty(), null, null, null, LocalTime.of(15, 0));
    }

    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       LocalDate checkInDate, int stayNights, String rawRequestText, GuestPreference preference) {
        this(reservationId, guestName, bookedRoomType, checkInDate, stayNights, 1,
                rawRequestText, preference, TagPreference.empty(), null, null, null, LocalTime.of(15, 0));
    }

    // ==========================================
    // 예약 상태 전이 (State Transition) 도메인 로직
    // ==========================================

    public void assignRoom(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new IllegalArgumentException("배정할 객실 번호가 올바르지 않습니다.");
        }
        this.assignedRoomNumber = roomNumber.trim();
        this.status = ReservationStatus.ASSIGNED;
    }

    public void cancelAssignment() {
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.PENDING;
    }

    public void markDueIn() {
        if (this.assignedRoomNumber == null) {
            throw new IllegalStateException("객실이 배정되지 않은 상태에서는 도착 예정 단계로 변경할 수 없습니다.");
        }
        this.status = ReservationStatus.DUE_IN;
    }

    public void markReadyForCheckIn() {
        markDueIn();
    }

    public void checkIn() {
        if (this.assignedRoomNumber == null) {
            throw new IllegalStateException("객실이 배정되지 않은 예약은 체크인(입실)할 수 없습니다.");
        }
        if (this.breakfastOption.isIncluded() && !this.breakfastOption.isTicketsIssued()) {
            this.breakfastOption.issueTickets();
        }
        this.status = ReservationStatus.CHECKED_IN;
    }

    public void startStaying() {
        checkIn();
    }

    public void changeRoom(String newRoomNumber) {
        if (newRoomNumber == null || newRoomNumber.isBlank()) {
            throw new IllegalArgumentException("이동할 신규 객실 번호가 올바르지 않습니다.");
        }
        this.previousRoomNumber = this.assignedRoomNumber;
        this.assignedRoomNumber = newRoomNumber.trim();
    }

    public void checkOut() {
        checkOut(LocalDate.now());
    }

    /**
     * 실제 발생 영업일자를 주입받는 체크아웃 메서드
     */
    public void checkOut(LocalDate effectiveDate) {
        if (!this.status.isInHouse()) {
            throw new IllegalStateException("현재 숙박 중인 고객만 체크아웃할 수 있습니다.");
        }

        long balance = this.paymentLedger.getBalance();
        if (balance > 0) {
            throw new IllegalStateException("미정산 금액(" + balance + "원)이 남아있어 체크아웃할 수 없습니다.");
        }
        if (balance < 0) {
            throw new IllegalStateException("초과 수납/환불 대상 금액(" + Math.abs(balance) + "원)이 남아있어 체크아웃할 수 없습니다.");
        }

        this.actualCheckOutDate = (effectiveDate != null) ? effectiveDate : LocalDate.now();
        this.status = ReservationStatus.CHECKED_OUT;
    }

    public void cancelReservation() {
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.CANCELLED;
    }

    public void grantLateCheckOut(LocalTime time) {
        this.lateCheckOutTime = Objects.requireNonNull(time, "연장 시간은 필수입니다.");
    }

    public boolean isAssigned() {
        return this.assignedRoomNumber != null && this.status.hasAssignedRoom();
    }

    public Reservation withPreference(GuestPreference newPreference) {
        Reservation cloned = new Reservation(
                this.reservationId,
                this.guestName,
                this.bookedRoomType,
                this.checkInDate,
                this.stayNights,
                this.guestCount,
                this.rawRequestText,
                newPreference,
                this.tagPreference,
                this.channelInfo,
                this.breakfastOption,
                this.paymentLedger,
                this.estimatedArrivalTime
        );
        cloned.status = this.status;
        cloned.assignedRoomNumber = this.assignedRoomNumber;
        cloned.previousRoomNumber = this.previousRoomNumber;
        cloned.actualCheckOutDate = this.actualCheckOutDate;
        cloned.lateCheckOutTime = this.lateCheckOutTime;
        return cloned;
    }

    public Reservation withTagPreference(TagPreference newTagPreference) {
        Reservation cloned = new Reservation(
                this.reservationId,
                this.guestName,
                this.bookedRoomType,
                this.checkInDate,
                this.stayNights,
                this.guestCount,
                this.rawRequestText,
                this.preference,
                newTagPreference,
                this.channelInfo,
                this.breakfastOption,
                this.paymentLedger,
                this.estimatedArrivalTime
        );
        cloned.status = this.status;
        cloned.assignedRoomNumber = this.assignedRoomNumber;
        cloned.previousRoomNumber = this.previousRoomNumber;
        cloned.actualCheckOutDate = this.actualCheckOutDate;
        cloned.lateCheckOutTime = this.lateCheckOutTime;
        return cloned;
    }

    public LocalDate getCheckOutDate() {
        return (this.checkInDate != null) ? this.checkInDate.plusDays(this.stayNights) : null;
    }

    // Getters
    public String getReservationId() { return reservationId; }
    public String getGuestName() { return guestName; }
    public RoomType getBookedRoomType() { return bookedRoomType; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public int getStayNights() { return stayNights; }
    public int getGuestCount() { return guestCount; }
    public String getRawRequestText() { return rawRequestText; }
    public GuestPreference getPreference() { return preference; }
    public TagPreference getTagPreference() { return tagPreference != null ? tagPreference : TagPreference.empty(); }
    public BookingChannelInfo getChannelInfo() { return channelInfo; }
    public BreakfastOption getBreakfastOption() { return breakfastOption; }
    public PaymentLedger getPaymentLedger() { return paymentLedger; }
    public LocalTime getEstimatedArrivalTime() { return estimatedArrivalTime; }
    public LocalTime getLateCheckOutTime() { return lateCheckOutTime; }
    public String getAssignedRoomNumber() { return assignedRoomNumber; }
    public String getPreviousRoomNumber() { return previousRoomNumber; }
    public LocalDate getActualCheckOutDate() { return actualCheckOutDate; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = Objects.requireNonNull(status); }

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
        return String.format("[%s | %s | %s | %s~%s (%d박) | 상태:%s | 배정:%s%s]",
                reservationId, guestName, bookedRoomType.getDescription(),
                checkInDate, getCheckOutDate(), stayNights,
                status.getTitle(),
                isAssigned() ? (assignedRoomNumber + "호") : "미배정",
                (previousRoomNumber != null) ? (" (이전: " + previousRoomNumber + "호)") : ""
        );
    }
}