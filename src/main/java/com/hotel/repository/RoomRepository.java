package com.hotel.repository;

import com.hotel.domain.Room;

import java.util.List;
import java.util.Optional;

/**
 * 191실 물리 객실 도면 및 룸 랙 데이터 접근을 위한 표준 저장소 인터페이스.
 *
 * <p>도메인 및 서비스 계층은 저장 매체(인메모리, RDB, NoSQL)에 구애받지 않고 본 규약에만 의존합니다.</p>
 */
public interface RoomRepository {

    void save(Room room);

    Optional<Room> findByRoomNumber(String roomNumber);

    List<Room> findAll();
}