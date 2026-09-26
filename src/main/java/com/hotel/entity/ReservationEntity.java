package com.hotel.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.*;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "reservations",
        indexes = {
                @Index(name = "idx_rsv_checkin", columnList = "operational_check_in_date"),
                @Index(name = "idx_rsv_status", columnList = "status"),
                @Index(name = "idx_rsv_room", columnList = "assigned_room_number"),
                @Index(name = "idx_rsv_channel", columnList = "channel_type")
        }
)
public class ReservationEntity {

    @Id
    @Column(name = "reservation_id", length = 50)
    private String reservationId;

    @Column(name = "original_guest_name", nullable = false, length = 100)
    private String originalGuestName;

    @Enumerated(EnumType.STRING)
    @Column(name = "booked_room_type", nullable = false, length = 30)
    private RoomType bookedRoomType;

    @Column(name = "contract_check_in_date", nullable = false)
    private LocalDate contractCheckInDate;

    @Column(name = "contract_stay_nights", nullable = false)
    private int contractStayNights;

    @Column(name = "raw_request_text", length = 1000)
    private String rawRequestText;

    @Lob
    @Column(name = "raw_xml_payload")
    private String rawXmlPayload;

    @Column(name = "operational_guest_name", length = 100)
    private String operationalGuestName;

    @Column(name = "operational_check_in_date")
    private LocalDate operationalCheckInDate;

    @Column(name = "operational_stay_nights")
    private int operationalStayNights;

    @Column(name = "internal_staff_memo", length = 1000)
    private String internalStaffMemo;

    @Column(name = "assigned_room_number", length = 10)
    private String assignedRoomNumber;

    @Column(name = "previous_room_number", length = 10)
    private String previousRoomNumber;

    @Column(name = "actual_check_out_date")
    private LocalDate actualCheckOutDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "preferred_tags_csv", length = 500)
    private String preferredTagsCsv;

    @Column(name = "avoid_tags_csv", length = 500)
    private String avoidTagsCsv;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", length = 30)
    private BookingChannelInfo.ChannelType channelType;

    @Column(name = "channel_rsv_no", length = 100)
    private String channelReservationNo;

    @Column(name = "plan_name", length = 200)
    private String planName;

    @Column(name = "breakfast_included")
    private boolean breakfastIncluded;

    @Column(name = "daily_breakfast_count")
    private int dailyBreakfastCount;

    @Column(name = "breakfast_tickets_issued")
    private boolean breakfastTicketsIssued;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 30)
    private PaymentLedger.PaymentType paymentType;

    @Column(name = "total_charges")
    private long totalCharges;

    @Column(name = "total_payments")
    private long totalPayments;

    @Column(name = "estimated_arrival_time")
    private LocalTime estimatedArrivalTime;

    @Column(name = "late_check_out_time")
    private LocalTime lateCheckOutTime;

    // 🚀 [신규] 일자별 1박 단가 JSON 문자열 저장 컬럼
    @Lob
    @Column(name = "daily_rates_json")
    private String dailyRatesJson;

    protected ReservationEntity() {}

    public static ReservationEntity fromDomain(Reservation domain) {
        ReservationEntity entity = new ReservationEntity();
        entity.reservationId = domain.getReservationId();
        entity.originalGuestName = domain.getOriginalGuestName();
        entity.bookedRoomType = domain.getBookedRoomType();
        entity.contractCheckInDate = domain.getContractCheckInDate();
        entity.contractStayNights = domain.getContractStayNights();
        entity.rawRequestText = domain.getRawRequestText();
        entity.rawXmlPayload = domain.getRawXmlPayload();

        entity.operationalGuestName = domain.getOperationalGuestName();
        entity.operationalCheckInDate = domain.getOperationalCheckInDate();
        entity.operationalStayNights = domain.getOperationalStayNights();
        entity.internalStaffMemo = domain.getInternalStaffMemo();
        entity.assignedRoomNumber = domain.getAssignedRoomNumber();
        entity.previousRoomNumber = domain.getPreviousRoomNumber();
        entity.actualCheckOutDate = domain.getActualCheckOutDate();
        entity.status = domain.getStatus();

        if (domain.getTagPreference() != null) {
            entity.preferredTagsCsv = String.join(",", domain.getTagPreference().preferredTags());
            entity.avoidTagsCsv = String.join(",", domain.getTagPreference().avoidTags());
        }

        if (domain.getChannelInfo() != null) {
            entity.channelType = domain.getChannelInfo().channelType();
            entity.channelReservationNo = domain.getChannelInfo().channelReservationNo();
            entity.planName = domain.getChannelInfo().planName();
        }

        if (domain.getBreakfastOption() != null) {
            entity.breakfastIncluded = domain.getBreakfastOption().isIncluded();
            entity.dailyBreakfastCount = domain.getBreakfastOption().getDailyBreakfastCount();
            entity.breakfastTicketsIssued = domain.getBreakfastOption().isTicketsIssued();
        }

        if (domain.getPaymentLedger() != null) {
            entity.paymentType = domain.getPaymentLedger().getPaymentType();
            entity.totalCharges = domain.getPaymentLedger().getTotalCharges();
            entity.totalPayments = domain.getPaymentLedger().getTotalPayments();
        }

        entity.estimatedArrivalTime = domain.getEstimatedArrivalTime();
        entity.lateCheckOutTime = domain.getLateCheckOutTime();

        // 🚀 일자별 요금 스케줄 직렬화
        if (domain.getDailyRateSchedule() != null) {
            try {
                Map<String, Long> rateMap = new LinkedHashMap<>();
                domain.getDailyRateSchedule().getDailyRates().forEach((date, rate) -> rateMap.put(date.toString(), rate));
                entity.dailyRatesJson = new ObjectMapper().writeValueAsString(rateMap);
            } catch (Exception ignored) {}
        }

        return entity;
    }

    public Reservation toDomain() {
        Set<String> prefSet = parseCsvToSet(this.preferredTagsCsv);
        Set<String> avoidSet = parseCsvToSet(this.avoidTagsCsv);
        TagPreference tagPref = new TagPreference(prefSet, avoidSet);

        BookingChannelInfo channel = (this.channelType != null)
                ? new BookingChannelInfo(this.channelType, this.channelReservationNo, this.planName)
                : BookingChannelInfo.direct(this.reservationId);

        BreakfastOption breakfast = this.breakfastIncluded
                ? BreakfastOption.included(this.dailyBreakfastCount)
                : BreakfastOption.none();
        if (this.breakfastTicketsIssued) {
            breakfast.issueTickets();
        }

        PaymentLedger payment = new PaymentLedger(
                this.paymentType != null ? this.paymentType : PaymentLedger.PaymentType.PAY_ON_ARRIVAL,
                this.totalCharges
        );
        long diff = this.totalPayments - (this.paymentType == PaymentLedger.PaymentType.PREPAID ? this.totalCharges : 0);
        if (diff > 0) {
            payment.recordPayment(diff);
        }

        Reservation domain = new Reservation(
                this.reservationId,
                this.originalGuestName,
                this.bookedRoomType,
                this.contractCheckInDate,
                this.contractStayNights,
                1,
                this.rawRequestText,
                this.rawXmlPayload,
                GuestPreference.empty(),
                tagPref,
                channel,
                breakfast,
                payment,
                this.estimatedArrivalTime
        );

        domain.updateOperationalDetails(
                this.operationalGuestName,
                this.operationalCheckInDate,
                this.operationalStayNights,
                this.internalStaffMemo
        );

        if (this.assignedRoomNumber != null) {
            domain.assignRoom(this.assignedRoomNumber);
        }
        if (this.previousRoomNumber != null) {
            domain.changeRoom(this.assignedRoomNumber);
        }

        if (this.status == ReservationStatus.CHECKED_IN) {
            domain.checkIn();
        } else if (this.status == ReservationStatus.CHECKED_OUT) {
            domain.checkIn();
            domain.checkOut(this.actualCheckOutDate);
        } else if (this.status == ReservationStatus.CANCELLED) {
            domain.cancelReservation();
        }

        if (this.lateCheckOutTime != null) {
            domain.grantLateCheckOut(this.lateCheckOutTime);
        }

        // 🚀 일자별 요금 스케줄 역직렬화
        if (this.dailyRatesJson != null && !this.dailyRatesJson.isBlank()) {
            try {
                ObjectMapper om = new ObjectMapper();
                Map<String, Object> map = om.readValue(this.dailyRatesJson, new TypeReference<>() {});
                Map<LocalDate, Long> rates = new LinkedHashMap<>();
                map.forEach((dateStr, rateVal) -> rates.put(LocalDate.parse(dateStr), Long.valueOf(rateVal.toString())));
                domain.updateDailyRates(rates);
            } catch (Exception ignored) {}
        }

        return domain;
    }

    private Set<String> parseCsvToSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String getReservationId() { return reservationId; }
    public String getOriginalGuestName() { return originalGuestName; }
    public RoomType getBookedRoomType() { return bookedRoomType; }
    public LocalDate getContractCheckInDate() { return contractCheckInDate; }
    public int getContractStayNights() { return contractStayNights; }
    public String getRawRequestText() { return rawRequestText; }
    public String getRawXmlPayload() { return rawXmlPayload; }
    public String getOperationalGuestName() { return operationalGuestName; }
    public LocalDate getOperationalCheckInDate() { return operationalCheckInDate; }
    public int getOperationalStayNights() { return operationalStayNights; }
    public String getInternalStaffMemo() { return internalStaffMemo; }
    public String getAssignedRoomNumber() { return assignedRoomNumber; }
    public String getPreviousRoomNumber() { return previousRoomNumber; }
    public LocalDate getActualCheckOutDate() { return actualCheckOutDate; }
    public ReservationStatus getStatus() { return status; }
    public BookingChannelInfo.ChannelType getChannelType() { return channelType; }
    public String getChannelReservationNo() { return channelReservationNo; }
    public String getPlanName() { return planName; }
    public String getDailyRatesJson() { return dailyRatesJson; }
}