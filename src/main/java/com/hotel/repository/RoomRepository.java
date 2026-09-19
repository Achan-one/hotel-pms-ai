package com.hotel.repository;

import com.hotel.domain.Room;
import com.hotel.domain.RoomType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoomRepository {
    private final Map<String, Room> roomStore = new HashMap<>();

    public RoomRepository() {
        initRooms();
    }

    public void save(Room room) {
        roomStore.put(room.getRoomNumber(), room);
    }

    public Optional<Room> findByRoomNumber(String roomNumber) {
        return Optional.ofNullable(roomStore.get(roomNumber));
    }

    public List<Room> findAll() {
        return new ArrayList<>(roomStore.values());
    }

    private void initRooms() {
        for (int floor = 3; floor <= 15; floor++) {
            for (int roomNum = 1; roomNum <= 16; roomNum++) {
                // 1. 전 층 13호 단일 결번 (서양권 금기 번호)
                if (roomNum == 13) continue;

                // 2. 14층, 15층 특수 결번 (3호, 7호)
                if (floor >= 14 && (roomNum == 3 || roomNum == 7)) continue;

                String roomNumber = String.format("%02d%02d", floor, roomNum);

                // 코너룸 및 엘리베이터 인접 정의
                boolean isCorner = (roomNum == 1 || roomNum == 2 || roomNum == 9 || roomNum == 14 || roomNum == 16);
                boolean isNearElevator = (roomNum >= 3 && roomNum <= 8);

                // 객실 타입 매핑
                RoomType type;
                if (roomNum == 1 || roomNum == 14) {
                    type = RoomType.SUPERIOR_TWIN;
                } else if (roomNum == 2 || roomNum == 9) {
                    type = RoomType.RESIDENTIAL_DOUBLE;
                } else if (roomNum == 5 || roomNum == 6 || roomNum == 10 || roomNum == 11) {
                    type = RoomType.SUPERIOR_DOUBLE;
                } else if (floor >= 14 && (roomNum == 4 || roomNum == 8)) {
                    type = RoomType.EXECUTIVE_DOUBLE;
                } else {
                    // 3, 4, 7, 8(13층 이하), 12, 15, 16호
                    type = RoomType.MODERATE_DOUBLE;
                }

                Room room = new Room(roomNumber, floor, type, isNearElevator, isCorner);
                save(room);
            }
        }
    }
}