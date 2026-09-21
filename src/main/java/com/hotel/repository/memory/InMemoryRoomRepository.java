package com.hotel.repository.memory;

import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap 기반 인메모리 객실 저장소 구현체.
 * 서양권 금기 13호 전 층 결번 및 14~15층 설비 결번(3호, 7호) 도면 규격을 자동으로 초기화합니다.
 */
public class InMemoryRoomRepository implements RoomRepository {

    private final Map<String, Room> roomStore = new ConcurrentHashMap<>();

    public InMemoryRoomRepository() {
        initRooms();
    }

    @Override
    public void save(Room room) {
        if (room != null) {
            roomStore.put(room.getRoomNumber(), room);
        }
    }

    @Override
    public Optional<Room> findByRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomStore.get(roomNumber.trim()));
    }

    @Override
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

                boolean isCorner = (roomNum == 1 || roomNum == 2 || roomNum == 9 || roomNum == 14 || roomNum == 16);
                boolean isNearElevator = (roomNum >= 3 && roomNum <= 8);

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
                    type = RoomType.MODERATE_DOUBLE;
                }

                Room room = new Room(roomNumber, floor, type, isNearElevator, isCorner);
                save(room);
            }
        }
    }
}