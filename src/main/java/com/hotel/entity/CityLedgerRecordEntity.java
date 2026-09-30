package com.hotel.entity;

import com.hotel.domain.BookingChannelInfo;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;
import java.time.LocalDateTime;

@Entity
@Table(name = "city_ledger_records", indexes = {
        @Index(name = "idx_city_ledger_channel", columnList = "channel_type"),
        @Index(name = "idx_city_ledger_date", columnList = "settled_date")
})
public class CityLedgerRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 30)
    private BookingChannelInfo.ChannelType channelType;

    @Column(name = "reservation_id", nullable = false, length = 50)
    private String reservationId;

    @Column(name = "guest_name", nullable = false, length = 100)
    private String guestName;

    @Column(name = "channel_rsv_no", length = 100)
    private String channelReservationNo;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "billed_amount", nullable = false)
    private long billedAmount;

    @Column(name = "settled_date", nullable = false)
    private LocalDate settledDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CityLedgerRecordEntity() {}

    public CityLedgerRecordEntity(BookingChannelInfo.ChannelType channelType,
                                  String reservationId,
                                  String guestName,
                                  String channelReservationNo,
                                  LocalDate checkInDate,
                                  LocalDate checkOutDate,
                                  long billedAmount,
                                  LocalDate settledDate) {
        if (billedAmount < 0) {
            throw new IllegalArgumentException("정산 금액은 0 이상이어야 합니다.");
        }
        this.channelType = Objects.requireNonNull(channelType, "채널 유형은 필수입니다.");
        this.reservationId = Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        this.guestName = Objects.requireNonNull(guestName, "투숙객명은 필수입니다.");
        this.channelReservationNo = channelReservationNo;
        this.checkInDate = Objects.requireNonNull(checkInDate, "체크인 일자는 필수입니다.");
        this.checkOutDate = Objects.requireNonNull(checkOutDate, "체크아웃 일자는 필수입니다.");
        this.billedAmount = billedAmount;
        this.settledDate = Objects.requireNonNull(settledDate, "정산 일자는 필수입니다.");
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public BookingChannelInfo.ChannelType getChannelType() { return channelType; }
    public String getReservationId() { return reservationId; }
    public String getGuestName() { return guestName; }
    public String getChannelReservationNo() { return channelReservationNo; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public long getBilledAmount() { return billedAmount; }
    public LocalDate getSettledDate() { return settledDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}