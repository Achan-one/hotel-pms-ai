package com.hotel.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class Reservation {
    // ==========================================
    // 1. [불변] OTA / 채널 매니저 원천 계약 원장 (Audit Trail)
    //    * 어떤 프론트 조작으로도 절대 변경 불가!
    // ==========================================
    private final String reservationId;
    private final String originalGuestName;       // OTA 인입 시점 원문 고객명
    private final RoomType bookedRoomType;         // OTA 계약 룸타입 (정산 기준)
    private final LocalDate contractCheckInDate;   // OTA 계약 체크인 일자
    private final int contractStayNights;          // OTA 계약 박수
    private final String rawRequestText;           // OTA 인입 고객 원문 요청사항
    private final String rawXmlPayload;            // OTA 전문 원형 (TLX XML 전문 원본)

    // ==========================================
    // 2. [가변] PMS 현장 운영 오버라이드 필드
    //    * 프론트 현장 수정 및 룸체인지/일정조정 시 여기에만 반영
    // ==========================================
    private String operationalGuestName;           // 현장 수정 투숙객 실명
    private LocalDate operationalCheckInDate;      // 현장 조정 체크인 일자
    private int operationalStayNights;             // 현장 연장/단축 반영 실 숙박 박수
    private String internalStaffMemo;              // 호텔 직원 내부 인계 메모
    private String assignedRoomNumber;             // 실제 실물 배정 호실 (업그레이드 등)
    private String previousRoomNumber;             // 이전 호실 이력
    private LocalDate actualCheckOutDate;          // 실제 퇴실일
    private ReservationStatus status;

    // 3. 부가 도메인 VO
    private final GuestPreference preference;
    private final TagPreference tagPreference;
    private final BookingChannelInfo channelInfo;
    private final BreakfastOption breakfastOption;
    private final PaymentLedger paymentLedger;
    private LocalTime estimatedArrivalTime;
    private LocalTime lateCheckOutTime;

    // 14개 인자 마스터 생성자
    public Reservation(String reservationId,
                       String guestName,
                       RoomType bookedRoomType,
                       LocalDate checkInDate,
                       int stayNights,
                       int guestCount,
                       String rawRequestText,
                       String rawXmlPayload,
                       GuestPreference preference,
                       TagPreference tagPreference,
                       BookingChannelInfo channelInfo,
                       BreakfastOption breakfastOption,
                       PaymentLedger paymentLedger,
                       LocalTime estimatedArrivalTime) {

        this.reservationId = Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        this.originalGuestName = Objects.requireNonNull(guestName, "고객명은 필수입니다.");
        this.bookedRoomType = Objects.requireNonNull(bookedRoomType, "계약 객실 타입은 필수입니다.");
        this.contractCheckInDate = Objects.requireNonNull(checkInDate, "계약 체크인 일자는 필수입니다.");
        this.contractStayNights = Math.max(1, stayNights);
        this.rawRequestText = rawRequestText;
        this.rawXmlPayload = rawXmlPayload;

        // 초기 운영값은 계약 원천값으로 초기화
        this.operationalGuestName = this.originalGuestName;
        this.operationalCheckInDate = this.contractCheckInDate;
        this.operationalStayNights = this.contractStayNights;
        this.internalStaffMemo = "";

        this.preference = (preference != null) ? preference : GuestPreference.empty();
        this.tagPreference = (tagPreference != null) ? tagPreference : TagPreference.empty();
        this.channelInfo = (channelInfo != null) ? channelInfo : BookingChannelInfo.direct(reservationId);
        this.breakfastOption = (breakfastOption != null) ? breakfastOption : BreakfastOption.none();
        this.paymentLedger = (paymentLedger != null) ? paymentLedger : new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 0);
        this.estimatedArrivalTime = (estimatedArrivalTime != null) ? estimatedArrivalTime : LocalTime.of(15, 0);

        this.assignedRoomNumber = null;
        this.previousRoomNumber = null;
        this.actualCheckOutDate = null;
        this.status = ReservationStatus.PENDING;
    }

    // [13개 인자 호환 생성자 - ReportExportServiceTest 등 기존 도메인 테스트 호환용]
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
        this(reservationId, guestName, bookedRoomType, checkInDate, stayNights, guestCount,
                rawRequestText, null, preference, tagPreference, channelInfo, breakfastOption, paymentLedger, estimatedArrivalTime);
    }

    // [12개 인자 호환 생성자 - ReservationDomainTest 등]
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
                rawRequestText, null, preference, TagPreference.empty(), channelInfo, breakfastOption, paymentLedger, estimatedArrivalTime);
    }

    // [7개 인자 호환 생성자]
    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       LocalDate checkInDate, int stayNights, String rawRequestText, GuestPreference preference) {
        this(reservationId, guestName, bookedRoomType, checkInDate, stayNights, 1,
                rawRequestText, null, preference, TagPreference.empty(), null, null, null, LocalTime.of(15, 0));
    }

    // [6개 인자 호환 생성자]
    public Reservation(String reservationId, String guestName, RoomType bookedRoomType,
                       int stayNights, String rawRequestText, GuestPreference preference) {
        this(reservationId, guestName, bookedRoomType, LocalDate.now(), stayNights, 1,
                rawRequestText, null, preference, TagPreference.empty(), null, null, null, LocalTime.of(15, 0));
    }

    // ==========================================
    // PMS 현장 관리용 수정 메서드 (원천 계약 원장은 절대 손대지 않음!)
    // ==========================================

    public void updateOperationalDetails(String newGuestName, LocalDate newCheckInDate, Integer newStayNights, String staffMemo) {
        if (newGuestName != null && !newGuestName.isBlank()) {
            this.operationalGuestName = newGuestName.trim();
        }
        if (newCheckInDate != null) {
            this.operationalCheckInDate = newCheckInDate;
        }
        if (newStayNights != null && newStayNights > 0) {
            this.operationalStayNights = newStayNights;
        }
        if (staffMemo != null) {
            this.internalStaffMemo = staffMemo.trim();
        }
    }

    public void assignRoom(String roomNumber) {
        this.assignedRoomNumber = Objects.requireNonNull(roomNumber, "호실 번호는 필수입니다.").trim();
        this.status = ReservationStatus.ASSIGNED;
    }

    public void cancelAssignment() {
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.PENDING;
    }

    public void checkIn() {
        if (this.assignedRoomNumber == null) {
            throw new IllegalStateException("객실이 배정되지 않은 상태에서는 체크인할 수 없습니다.");
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
        this.previousRoomNumber = this.assignedRoomNumber;
        this.assignedRoomNumber = Objects.requireNonNull(newRoomNumber, "신규 호실 번호는 필수입니다.").trim();
    }

    public void checkOut() {
        checkOut(LocalDate.now());
    }

    public void checkOut(LocalDate effectiveDate) {
        if (!this.status.isInHouse()) {
            throw new IllegalStateException("현재 숙박 중인 고객만 체크아웃할 수 있습니다.");
        }
        if (this.paymentLedger != null && !this.paymentLedger.isSettled()) {
            long due = this.paymentLedger.getTotalDue();
            if (due > 0) {
                throw new IllegalStateException("미정산 금액(" + due + "원)이 남아있어 체크아웃할 수 없습니다.");
            }
        }
        this.actualCheckOutDate = (effectiveDate != null) ? effectiveDate : LocalDate.now();
        this.status = ReservationStatus.CHECKED_OUT;
    }

    public void cancelReservation() {
        this.assignedRoomNumber = null;
        this.status = ReservationStatus.CANCELLED;
    }

    // ==========================================
    // Getters: 계약 원천값과 운영값 분리 제공
    // ==========================================

    // [불변 계약 원장]
    public String getReservationId() { return reservationId; }
    public String getOriginalGuestName() { return originalGuestName; }
    public RoomType getBookedRoomType() { return bookedRoomType; }
    public LocalDate getContractCheckInDate() { return contractCheckInDate; }
    public int getContractStayNights() { return contractStayNights; }
    public String getRawRequestText() { return rawRequestText; }
    public String getRawXmlPayload() { return rawXmlPayload; }

    // [현장 운영 상태]
    public String getOperationalGuestName() { return operationalGuestName; }
    public LocalDate getOperationalCheckInDate() { return operationalCheckInDate; }
    public int getOperationalStayNights() { return operationalStayNights; }
    public String getInternalStaffMemo() { return internalStaffMemo; }

    // 기존 도메인 인터페이스 호환 게터
    public String getGuestName() { return operationalGuestName; }
    public LocalDate getCheckInDate() { return operationalCheckInDate; }
    public int getStayNights() { return operationalStayNights; }
    public LocalDate getCheckOutDate() { return operationalCheckInDate.plusDays(operationalStayNights); }

    public String getAssignedRoomNumber() { return assignedRoomNumber; }
    public String getPreviousRoomNumber() { return previousRoomNumber; }
    public LocalDate getActualCheckOutDate() { return actualCheckOutDate; }
    public ReservationStatus getStatus() { return status; }
    public boolean isAssigned() { return assignedRoomNumber != null; }

    public GuestPreference getPreference() { return preference; }
    public TagPreference getTagPreference() { return tagPreference; }
    public BookingChannelInfo getChannelInfo() { return channelInfo; }
    public BreakfastOption getBreakfastOption() { return breakfastOption; }
    public PaymentLedger getPaymentLedger() { return paymentLedger; }
    public LocalTime getEstimatedArrivalTime() { return estimatedArrivalTime; }
    public LocalTime getLateCheckOutTime() { return lateCheckOutTime; }
    public void grantLateCheckOut(LocalTime time) { this.lateCheckOutTime = time; }

    public Reservation withPreference(GuestPreference newPreference) {
        Reservation clone = new Reservation(
                this.reservationId, this.originalGuestName, this.bookedRoomType,
                this.contractCheckInDate, this.contractStayNights, 1,
                this.rawRequestText, this.rawXmlPayload, newPreference,
                this.tagPreference, this.channelInfo, this.breakfastOption, this.paymentLedger, this.estimatedArrivalTime
        );
        clone.operationalGuestName = this.operationalGuestName;
        clone.operationalCheckInDate = this.operationalCheckInDate;
        clone.operationalStayNights = this.operationalStayNights;
        clone.internalStaffMemo = this.internalStaffMemo;
        clone.assignedRoomNumber = this.assignedRoomNumber;
        clone.previousRoomNumber = this.previousRoomNumber;
        clone.status = this.status;
        return clone;
    }

    public Reservation withTagPreference(TagPreference newTagPref) {
        Reservation clone = new Reservation(
                this.reservationId, this.originalGuestName, this.bookedRoomType,
                this.contractCheckInDate, this.contractStayNights, 1,
                this.rawRequestText, this.rawXmlPayload, this.preference,
                newTagPref, this.channelInfo, this.breakfastOption, this.paymentLedger, this.estimatedArrivalTime
        );
        clone.operationalGuestName = this.operationalGuestName;
        clone.operationalCheckInDate = this.operationalCheckInDate;
        clone.operationalStayNights = this.operationalStayNights;
        clone.internalStaffMemo = this.internalStaffMemo;
        clone.assignedRoomNumber = this.assignedRoomNumber;
        clone.previousRoomNumber = this.previousRoomNumber;
        clone.status = this.status;
        return clone;
    }
}