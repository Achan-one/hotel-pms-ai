package com.hotel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "room_schedules",
        indexes = {
                // 특정 날짜 구간에 겹치는 방을 초고속으로 색인하기 위한 복합 인덱스
                @Index(name = "idx_room_schedule_range", columnList = "room_number, check_in_date, check_out_date")
        }
)
public class RoomScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, length = 10)
    private String roomNumber;

    @Column(name = "reservation_id", nullable = false, length = 50)
    private String reservationId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    protected RoomScheduleEntity() {}

    public RoomScheduleEntity(String roomNumber, String reservationId, LocalDate checkInDate, LocalDate checkOutDate) {
        this.roomNumber = roomNumber;
        this.reservationId = reservationId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
    }

    public Long getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public String getReservationId() { return reservationId; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
}