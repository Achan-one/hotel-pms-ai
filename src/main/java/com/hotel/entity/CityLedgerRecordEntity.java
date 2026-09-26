package com.hotel.entity;

import com.hotel.domain.BookingChannelInfo;
import jakarta.persistence.*;

import java.time.LocalDate;
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
        this.channelType = channelType;
        this.reservationId = reservationId;
        this.guestName = guestName;
        this.channelReservationNo = channelReservationNo;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.billedAmount = billedAmount;
        this.settledDate = settledDate;
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