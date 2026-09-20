package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
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
    @DisplayName("공실로의 정상 룸 체인지 성공 및 기존 객실 스케줄 반납 검증")
    void changeRoom_Success() {
        // 실제 저장소의 첫 번째 객실 확보 (호실 번호 하드코딩 탈피)
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);
        RoomType targetType = originRoom.getRoomType();

        // 동일한 타입을 가지는 다른 대체 공실 탐색
        Room targetRoom = allRooms.stream()
                .filter(r -> r.getRoomType() == targetType && !r.getRoomNumber().equals(originRoom.getRoomNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("대체 가능한 동일 타입 객실이 없습니다."));

        Reservation res = new Reservation("RSV-MOVE-01", "Kim", targetType,
                checkIn, 2, "소음 민원으로 방 교체 요청", GuestPreference.empty());
        StayPeriod stayPeriod = new StayPeriod(checkIn, 2);
        originRoom.bookPeriod(stayPeriod);
        res.assignRoom(originRoom.getRoomNumber());

        // 룸 체인지 실행
        RoomChangeResult result = roomChangeService.changeRoom(res, targetRoom.getRoomNumber(), true);

        assertTrue(result.success());
        assertEquals(originRoom.getRoomNumber(), result.fromRoomNumber());
        assertEquals(targetRoom.getRoomNumber(), result.toRoomNumber());
        assertEquals(targetRoom.getRoomNumber(), res.getAssignedRoomNumber());

        // 기존 객실은 스케줄 반납(공실화), 신규 객실은 스케줄 점유(충돌 발생)
        assertFalse(originRoom.hasScheduleConflict(stayPeriod));
        assertTrue(targetRoom.hasScheduleConflict(stayPeriod));
    }

    @Test
    @DisplayName("이동 대상 객실에 이미 다른 예약이 있을 경우 충돌 거절 검증")
    void changeRoom_Conflict_Reject() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);
        Room targetRoom = allRooms.stream()
                .filter(r -> r.getRoomType() == originRoom.getRoomType() && !r.getRoomNumber().equals(originRoom.getRoomNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("테스트용 대체 객실이 없습니다."));

        Reservation res = new Reservation("RSV-MOVE-02", "Lee", originRoom.getRoomType(),
                checkIn, 2, "방 변경 요청", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());

        // 타겟 객실에 다른 투숙객이 이미 점유 중인 상황 설정
        StayPeriod conflictPeriod = new StayPeriod(checkIn, 2);
        targetRoom.bookPeriod(conflictPeriod);

        // 변경 시도 -> 스케줄 충돌로 거절되어야 함
        RoomChangeResult result = roomChangeService.changeRoom(res, targetRoom.getRoomNumber(), true);

        assertFalse(result.success());
        assertTrue(result.message().contains("이미 다른 예약이 확정"));
        assertEquals(originRoom.getRoomNumber(), res.getAssignedRoomNumber()); // 기존 방 유지
    }

    @Test
    @DisplayName("객실 타입이 맞지 않는 객실로의 이동 요청 시 거절 검증")
    void changeRoom_TypeMismatch_Reject() {
        List<Room> allRooms = roomRepository.findAll();
        Room originRoom = allRooms.get(0);

        // 타입이 서로 다른 객실 동적 탐색
        Room differentTypeRoom = allRooms.stream()
                .filter(r -> r.getRoomType() != originRoom.getRoomType())
                .findFirst()
                .orElseThrow(() -> new AssertionError("타입이 다른 객실을 찾을 수 없습니다."));

        Reservation res = new Reservation("RSV-MOVE-03", "Park", originRoom.getRoomType(),
                checkIn, 2, "방 바꿔주세요", GuestPreference.empty());
        res.assignRoom(originRoom.getRoomNumber());

        // 변경 시도 -> 타입 불일치로 거절되어야 함
        RoomChangeResult result = roomChangeService.changeRoom(res, differentTypeRoom.getRoomNumber(), true);

        assertFalse(result.success());
        assertTrue(result.message().contains("객실 타입 불일치"));
        assertEquals(originRoom.getRoomNumber(), res.getAssignedRoomNumber());
    }
}