package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomChangeServiceTest {

    private RoomRepository roomRepository;
    private RoomChangeService roomChangeService;
    private final LocalDate checkIn = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        roomRepository = new InMemoryRoomRepository();
        roomChangeService = new RoomChangeService(roomRepository);
    }

    @Test
    @DisplayName("[잔여 박수 분할] 3박 투숙 중 둘째 날 룸 무브 시 어제 투숙은 기존 방에 남고 남은 2박만 새 방으로 이전된다")
    void changeRoom_PartialRemainingNightsSplit() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);
        RoomType targetType = originRoom.getRoomType();

        Room targetRoom = allRooms.stream()
                .filter(r -> r.getRoomType() == targetType && !r.getRoomNumber().equals(originRoom.getRoomNumber()))
                .findFirst()
                .orElseThrow();

        // 9/20 체크인 3박 투숙 (9/20, 9/21, 9/22 투숙, 9/23 퇴실)
        Reservation res = new Reservation("RSV-MOVE-01", "Kim", targetType,
                checkIn, 3, "소음 민원", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());
        res.startStaying();

        originRoom.bookPeriod(new StayPeriod(checkIn, 3));
        originRoom.setStatus(RoomStatus.OCCUPIED);

        targetRoom.setStatus(RoomStatus.VACANT);

        // 둘째 날(9/21) 룸 체인지 실행 (잔여 2박)
        LocalDate moveDate = checkIn.plusDays(1);
        RoomChangeRequest request = new RoomChangeRequest("RSV-MOVE-01", targetRoom.getRoomNumber(), moveDate, "소음");

        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertTrue(result.success());
        assertEquals(2, result.remainingNights());
        assertEquals(targetRoom.getRoomNumber(), res.getAssignedRoomNumber());
        assertEquals(originRoom.getRoomNumber(), res.getPreviousRoomNumber());

        assertEquals(ReservationStatus.CHECKED_IN, res.getStatus(), "룸 체인지 후에도 예약의 본질 상태는 투숙중(CHECKED_IN)이어야 합니다.");
        assertTrue(res.getStatus().isInHouse());

        assertEquals(RoomStatus.OUT, originRoom.getStatus());
        assertEquals(RoomStatus.OCCUPIED, targetRoom.getStatus());

        assertFalse(originRoom.isAvailable(new StayPeriod(checkIn, 1)));
        assertTrue(originRoom.isAvailable(new StayPeriod(moveDate, 2)));
        assertFalse(targetRoom.isAvailable(new StayPeriod(moveDate, 2)));
    }

    @Test
    @DisplayName("[방어 1] 청소 대기(OUT) 상태인 객실로는 이동할 수 없다")
    void changeRoom_TargetNotVacant_Rejected() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);
        Room targetRoom = allRooms.stream()
                .filter(r -> r.getRoomType() == originRoom.getRoomType() && !r.getRoomNumber().equals(originRoom.getRoomNumber()))
                .findFirst()
                .orElseThrow();

        Reservation res = new Reservation("RSV-MOVE-02", "Lee", originRoom.getRoomType(),
                checkIn, 2, "방 변경 요청", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());
        res.startStaying();

        originRoom.bookPeriod(new StayPeriod(checkIn, 2));

        targetRoom.setStatus(RoomStatus.OUT);

        RoomChangeRequest request = new RoomChangeRequest("RSV-MOVE-02", targetRoom.getRoomNumber(), checkIn, "사유");
        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertFalse(result.success());
        assertTrue(result.message().contains("공실(VACANT)이 아닙니다"));
        assertEquals(originRoom.getRoomNumber(), res.getAssignedRoomNumber());
    }

    @Test
    @DisplayName("[업그레이드/다운그레이드] 객실 타입이 달라도(이종 룸타입) 룸 체인지가 정상 성공해야 한다")
    void changeRoom_CrossTypeUpgradeDowngrade_Success() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);

        // 이전 객실과 타입이 다른 공실 객실 탐색
        Room differentTypeRoom = allRooms.stream()
                .filter(r -> r.getRoomType() != originRoom.getRoomType())
                .filter(r -> r.getStatus().isAssignable())
                .findFirst()
                .orElseThrow();

        Reservation res = new Reservation("RSV-MOVE-03", "Park", originRoom.getRoomType(),
                checkIn, 2, "방 업그레이드 요청", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());
        res.startStaying();

        originRoom.bookPeriod(new StayPeriod(checkIn, 2));

        RoomChangeRequest request = new RoomChangeRequest("RSV-MOVE-03", differentTypeRoom.getRoomNumber(), checkIn, "VIP 업그레이드");
        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertTrue(result.success(), "다른 타입 객실로의 룸 체인지는 업그레이드/다운그레이드를 위해 성공해야 합니다.");
        assertEquals(differentTypeRoom.getRoomNumber(), res.getAssignedRoomNumber());
        assertEquals(ReservationStatus.CHECKED_IN, res.getStatus());
        assertEquals(RoomStatus.OUT, originRoom.getStatus());
        assertEquals(RoomStatus.OCCUPIED, differentTypeRoom.getStatus());
    }

    @Test
    @DisplayName("[0박 당일 룸 무브] 체크인 당일 입실 직후 방 변경 시 기존 방은 완전히 비워지고 새 방에 전체 박수가 등록된다")
    void changeRoom_SameDayCheckIn_ZeroNightsElapsed() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);
        RoomType targetType = originRoom.getRoomType();

        Room targetRoom = allRooms.stream()
                .filter(r -> r.getRoomType() == targetType && !r.getRoomNumber().equals(originRoom.getRoomNumber()))
                .findFirst()
                .orElseThrow();

        Reservation res = new Reservation("RSV-SAME-DAY", "Guest", targetType,
                checkIn, 2, "방 냄새 민원", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());
        res.checkIn();

        originRoom.bookPeriod(new StayPeriod(checkIn, 2));
        originRoom.setStatus(RoomStatus.OCCUPIED);
        targetRoom.setStatus(RoomStatus.VACANT);

        RoomChangeRequest request = new RoomChangeRequest("RSV-SAME-DAY", targetRoom.getRoomNumber(), checkIn, "냄새");
        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertTrue(result.success());
        assertEquals(2, result.remainingNights());
        assertEquals(ReservationStatus.CHECKED_IN, res.getStatus());
        assertEquals(RoomStatus.OUT, originRoom.getStatus());
        assertEquals(RoomStatus.OCCUPIED, targetRoom.getStatus());

        assertTrue(originRoom.getBookedPeriods().isEmpty());
        assertFalse(originRoom.isAssigned());
        assertFalse(targetRoom.isAvailable(new StayPeriod(checkIn, 2)));
    }
}