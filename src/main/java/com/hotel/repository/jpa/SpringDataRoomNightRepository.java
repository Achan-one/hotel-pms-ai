package com.hotel.repository.jpa;

import com.hotel.entity.RoomNightOccupancyEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataRoomNightRepository extends JpaRepository<RoomNightOccupancyEntity, RoomNightOccupancyEntity.Id> {

    // 방 행 락을 잡은 트랜잭션에서만 사용한다. 잠금 읽기라 스냅샷이 아닌 최신 값을 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM RoomNightOccupancyEntity n WHERE n.id.roomNumber = :roomNumber")
    List<RoomNightOccupancyEntity> findByRoomNumberForUpdate(@Param("roomNumber") String roomNumber);
}
