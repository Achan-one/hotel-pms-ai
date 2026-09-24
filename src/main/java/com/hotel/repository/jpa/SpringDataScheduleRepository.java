package com.hotel.repository.jpa;

import com.hotel.entity.RoomScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataScheduleRepository extends JpaRepository<RoomScheduleEntity, Long> {

    List<RoomScheduleEntity> findByRoomNumber(String roomNumber);

    List<RoomScheduleEntity> findByReservationId(String reservationId);

    // 반개구간 [checkIn, checkOut) 기준 충돌 스케줄 색인
    @Query("""
        SELECT s FROM RoomScheduleEntity s 
        WHERE s.roomNumber = :roomNumber 
          AND s.checkInDate < :checkOut 
          AND s.checkOutDate > :checkIn
    """)
    List<RoomScheduleEntity> findOverlappingSchedules(
            @Param("roomNumber") String roomNumber,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );

    void deleteByReservationId(String reservationId);
}