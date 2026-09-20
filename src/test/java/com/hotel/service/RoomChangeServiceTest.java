package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
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
        roomRepository = new RoomRepository();
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
        assertEquals(ReservationStatus.ROOM_CHANGED, res.getStatus());

        // 하우스키핑 상태 전이: 이전 방은 OUT, 새 방은 OCCUPIED
        assertEquals(RoomStatus.OUT, originRoom.getStatus());
        assertEquals(RoomStatus.OCCUPIED, targetRoom.getStatus());

        // 이전 방의 어제(9/20) 투숙 이력은 남아있고, 남은 9/21~9/23은 반납되어야 함
        assertFalse(originRoom.isAvailable(new StayPeriod(checkIn, 1)));
        assertTrue(originRoom.isAvailable(new StayPeriod(moveDate, 2)));

        // 새 방은 남은 2박(9/21~9/23)이 점유되어야 함
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

        // 타겟 객실이 청소 대기(OUT) 상태인 경우
        targetRoom.setStatus(RoomStatus.OUT);

        RoomChangeRequest request = new RoomChangeRequest("RSV-MOVE-02", targetRoom.getRoomNumber(), checkIn, "사유");
        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertFalse(result.success());
        assertTrue(result.message().contains("공실(VACANT)이 아닙니다"));
        assertEquals(originRoom.getRoomNumber(), res.getAssignedRoomNumber());
    }

    @Test
    @DisplayName("[방어 2] 객실 타입이 일치하지 않으면 이동이 거부된다")
    void changeRoom_TypeMismatch_Rejected() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);
        Room differentTypeRoom = allRooms.stream()
                .filter(r -> r.getRoomType() != originRoom.getRoomType())
                .findFirst()
                .orElseThrow();

        Reservation res = new Reservation("RSV-MOVE-03", "Park", originRoom.getRoomType(),
                checkIn, 2, "방 바꿔주세요", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());
        res.startStaying();

        RoomChangeRequest request = new RoomChangeRequest("RSV-MOVE-03", differentTypeRoom.getRoomNumber(), checkIn, "사유");
        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertFalse(result.success());
        assertTrue(result.message().contains("객실 타입 불일치"));
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

        // 체크인 당일(checkIn) 바로 룸 무브
        RoomChangeRequest request = new RoomChangeRequest("RSV-SAME-DAY", targetRoom.getRoomNumber(), checkIn, "냄새");
        RoomChangeResult result = roomChangeService.changeRoom(res, request);

        assertTrue(result.success());
        assertEquals(2, result.remainingNights());
        assertEquals(RoomStatus.OUT, originRoom.getStatus());
        assertEquals(RoomStatus.OCCUPIED, targetRoom.getStatus());

        // 기존 방은 어제 잔여 투숙도 없으므로 스케줄이 완전히 비워져야 함
        assertTrue(originRoom.getBookedPeriods().isEmpty());
        assertFalse(originRoom.isAssigned());

        // 신규 방은 2박 전체가 점유되어야 함
        assertFalse(targetRoom.isAvailable(new StayPeriod(checkIn, 2)));
    }
}