package com.hotel.repository;

import com.hotel.domain.Room;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    void save(Room room);

    Optional<Room> findByRoomNumber(String roomNumber);
    Optional<Room> findByRoomNumberForUpdate(String roomNumber);

    List<Room> findAll();

    /**
     * 방 하나에 태그를 붙인다. 다른 필드(상태, 스케줄)는 건드리지 않는다.
     * @return 해당 객실이 있으면 true
     */
    boolean addTag(String roomNumber, String tagCode);

    /**
     * 방 하나에서 태그를 뗀다. 다른 필드(상태, 스케줄)는 건드리지 않는다.
     * @return 해당 객실이 있으면 true
     */
    boolean removeTag(String roomNumber, String tagCode);

    /**
     * 모든 객실에서 태그를 뗀다. 태그를 삭제할 때 쓴다.
     */
    void removeTagFromAll(String tagCode);
}