package com.hotel.repository;

import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class RoomRepositoryTest {

    @Autowired
    private RoomRepository repository; // 👈 InMemory 대신 스프링 컨텍스트의 JpaRoomRepository 주입

    @Test
    @DisplayName("DB에 적재된 총 등록 객실 수는 정확히 191실이어야 한다")
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