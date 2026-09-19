package com.hotel.repository;

import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomRepositoryTest {

    private RoomRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RoomRepository();
    }

    @Test
    @DisplayName("총 등록 객실 수는 정확히 191실이어야 한다")
    void verifyTotalRoomCount() {
        List<Room> allRooms = repository.findAll();
        assertEquals(191, allRooms.size(), "전체 객실 수는 191개여야 합니다.");
    }

    @Test
    @DisplayName("전 층(3층~15층)에서 13호 객실은 결번(미존재)되어야 한다")
    void verifyNoRoom13OnAnyFloor() {
        for (int floor = 3; floor <= 15; floor++) {
            String roomNumber = String.format("%02d13", floor);
            Optional<Room> room = repository.findByRoomNumber(roomNumber);
            assertTrue(room.isEmpty(), roomNumber + "호는 결번 규정에 따라 존재하지 않아야 합니다.");
        }
    }

    @Test
    @DisplayName("상층부(14층, 15층)는 13호 외에 03호, 07호도 결번되어 층당 13실이어야 한다")
    void verifyTopFloorOmissions() {
        int[] topFloors = {14, 15};

        for (int floor : topFloors) {
            String room03 = String.format("%02d03", floor);
            String room07 = String.format("%02d07", floor);

            assertTrue(repository.findByRoomNumber(room03).isEmpty(), room03 + "호는 상층부 결번이어야 합니다.");
            assertTrue(repository.findByRoomNumber(room07).isEmpty(), room07 + "호는 상층부 결번이어야 합니다.");

            long countForFloor = repository.findAll().stream()
                    .filter(r -> r.getFloor() == floor)
                    .count();
            assertEquals(13, countForFloor, floor + "층의 객실 수는 13실이어야 합니다.");
        }
    }

    @Test
    @DisplayName("일반층(3층~13층)은 층당 정확히 15실이어야 한다")
    void verifyStandardFloorRoomCount() {
        for (int floor = 3; floor <= 13; floor++) {
            final int currentFloor = floor;
            long count = repository.findAll().stream()
                    .filter(r -> r.getFloor() == currentFloor)
                    .count();
            assertEquals(15, count, floor + "층의 객실 수는 15실이어야 합니다.");
        }
    }

    @Test
    @DisplayName("04호/08호는 14층 이상에서 이그제큐티브 더블이어야 한다")
    void verifyExecutiveDoubleOnUpperFloors() {
        Room room1404 = repository.findByRoomNumber("1404").orElseThrow();
        Room room1408 = repository.findByRoomNumber("1408").orElseThrow();
        Room room0404 = repository.findByRoomNumber("0404").orElseThrow();

        assertEquals(RoomType.EXECUTIVE_DOUBLE, room1404.getRoomType());
        assertEquals(RoomType.EXECUTIVE_DOUBLE, room1408.getRoomType());
        assertEquals(RoomType.MODERATE_DOUBLE, room0404.getRoomType());
    }
}