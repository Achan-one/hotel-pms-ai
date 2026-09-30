package com.hotel.repository.jpa;

import com.hotel.entity.RoomScheduleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SpringDataScheduleRepository extends JpaRepository<RoomScheduleEntity, Long> {

    List<RoomScheduleEntity> findByRoomNumber(String roomNumber);

    // 🚀 N+1 방어: 여러 객실의 스케줄을 단 1회의 IN 절 쿼리로 일괄 조회
    List<RoomScheduleEntity> findByRoomNumberIn(Collection<String> roomNumbers);

    // 방 행 락을 이미 잡은 트랜잭션에서만 사용한다. 잠금 읽기라 스냅샷이 아닌 최신 값을 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RoomScheduleEntity s WHERE s.roomNumber = :roomNumber")
    List<RoomScheduleEntity> findByRoomNumberForUpdate(@Param("roomNumber") String roomNumber);

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