package com.hotel.repository.jpa;

import com.hotel.domain.RoomType;
import com.hotel.entity.RoomEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataRoomRepository extends JpaRepository<RoomEntity, String> {

    List<RoomEntity> findByRoomType(RoomType roomType);

    // 객실 배정 및 상태 전이 시 동시성 충돌을 차단하기 위한 비관적 쓰기 락 (SELECT FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomEntity r WHERE r.roomNumber = :roomNumber")
    Optional<RoomEntity> findByRoomNumberForUpdate(@Param("roomNumber") String roomNumber);
}