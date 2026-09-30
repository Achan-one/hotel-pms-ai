package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.FloorMapResponseDto;
import com.hotel.service.dto.RoomMatrixItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloorStatusServiceTest {

    private RoomRepository roomRepository;
    private FloorStatusService floorStatusService;
    private final LocalDate targetDate = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        roomRepository = new com.hotel.repository.memory.InMemoryRoomRepository();
        floorStatusService = new FloorStatusService(roomRepository);
    }

    @Test
    @DisplayName("특정 날짜 기준 191실 층별 매트릭스 및 점유율(OCC) 정상 산출 검증")
    void getFloorMatrix_Success() {
        // 실제 저장소에 등록된 첫 번째 객실 번호 획득
        Room targetRoom = roomRepository.findAll().get(0);
        String targetRoomNo = targetRoom.getRoomNumber();
        int targetFloor = targetRoom.getFloor();

        Reservation res = new Reservation(
                "RSV-TEST-01",
                "Tanaka",
                targetRoom.getRoomType(),
                targetDate,
                2,
                "선호 요청",
                GuestPreference.empty()
        );
        res.assignRoom(targetRoomNo);

        FloorMapResponseDto response = floorStatusService.getFloorMatrix(targetDate, List.of(res));

        assertNotNull(response);
        assertEquals(191, response.totalRooms());
        assertEquals(1, response.occupiedRooms(), "배정된 객실 1건이 점유로 카운트되어야 합니다.");
        assertEquals(190, response.vacantRooms());

        List<RoomMatrixItemDto> targetFloorRooms = response.floorRooms().get(targetFloor);
        assertNotNull(targetFloorRooms);

        RoomMatrixItemDto matchedItem = targetFloorRooms.stream()
                .filter(r -> r.roomNumber().equals(targetRoomNo))
                .findFirst()
                .orElseThrow();

        assertEquals(RoomStatus.ASSIGNED, matchedItem.status());
        assertEquals("RSV-TEST-01", matchedItem.reservationId());
        assertEquals("Tanaka", matchedItem.guestName());
    }

    @Test
    @DisplayName("청소대기(OUT)나 점검 중인 방은 점유율 계산에서 점유로도 공실로도 세지 않는다")
    void getFloorMatrix_OutOfServiceRoomsAreNotCounted() {
        roomRepository.findByRoomNumber("0301").orElseThrow().restoreStatus(RoomStatus.OUT);
        roomRepository.findByRoomNumber("0302").orElseThrow().restoreStatus(RoomStatus.BLOCKED);

        FloorMapResponseDto response = floorStatusService.getFloorMatrix(targetDate, List.of());

        assertEquals(0, response.occupiedRooms());
        assertEquals(189, response.vacantRooms());
    }

    @Test
    @DisplayName("이미 퇴실한 예약도 머문 기간 안의 날짜에서는 투숙(OCCUPIED)으로 표시한다")
    void getFloorMatrix_CheckedOutReservationShowsOccupiedDuringStay() {
        Reservation res = new Reservation("RSV-CO-01", "Tanaka", RoomType.MODERATE_DOUBLE, targetDate, 3, "메모", GuestPreference.empty());
        res.assignRoom("0301");
        res.checkIn();
        res.checkOut(targetDate.plusDays(2));

        FloorMapResponseDto response = floorStatusService.getFloorMatrix(targetDate.plusDays(1), List.of(res));

        RoomMatrixItemDto item = response.floorRooms().get(3).stream()
                .filter(r -> r.roomNumber().equals("0301")).findFirst().orElseThrow();
        assertEquals(RoomStatus.OCCUPIED, item.status());
    }

    @Test
    @DisplayName("조회 일자는 필수다")
    void getFloorMatrix_RequiresDate() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> floorStatusService.getFloorMatrix(null, List.of()));
    }
}
