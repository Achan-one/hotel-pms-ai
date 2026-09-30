package com.hotel.repository.jpa;

import com.hotel.entity.RoomScheduleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SpringDataScheduleRepository extends JpaRepository<RoomScheduleEntity, Long> {

    List<RoomScheduleEntity> findByRoomNumber(String roomNumber);

    // N+1 방지: 여러 객실의 스케줄을 IN 절 한 번으로 조회
    List<RoomScheduleEntity> findByRoomNumberIn(Collection<String> roomNumbers);

    // 방 행 락을 이미 잡은 트랜잭션에서만 사용한다. 잠금 읽기라 스냅샷이 아닌 최신 값을 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RoomScheduleEntity s WHERE s.roomNumber = :roomNumber")
    List<RoomScheduleEntity> findByRoomNumberForUpdate(@Param("roomNumber") String roomNumber);
}