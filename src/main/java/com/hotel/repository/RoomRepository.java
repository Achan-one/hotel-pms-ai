package com.hotel.repository;

import com.hotel.domain.Room;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    void save(Room room);

    Optional<Room> findByRoomNumber(String roomNumber);
    Optional<Room> findByRoomNumberForUpdate(String roomNumber);

    List<Room> findAll();
}