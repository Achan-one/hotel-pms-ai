package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomAssignerTest {

    private RoomRepository repository;
    private RoomAssigner assigner;

    @BeforeEach
    void setUp() {
        repository = new RoomRepository();
        assigner = new RoomAssigner(repository);
    }

    @Test
    @DisplayName("동일한 객실에 대해 3박 이상 연박 투숙객은 1박 투숙객보다 점수가 높아야 한다")
    void verifyLongStayScoreMultiplier() {
        Room quietCornerRoom = repository.findByRoomNumber("1216").orElseThrow(); // 12층, 코너, 엘베이격

        GuestPreference pref = new GuestPreference(
                FloorPref.HIGH,
                ElevatorPref.AWAY,
                CornerPref.PREFER,
                true
        );

        int shortStayScore = assigner.calculateScore(quietCornerRoom, pref, 1);
        int longStayScore = assigner.calculateScore(quietCornerRoom, pref, 4);

        assertTrue(longStayScore > shortStayScore,
                String.format("연박 점수(%d)는 단박 점수(%d)보다 높아야 합니다.", longStayScore, shortStayScore));
    }

    @Test
    @DisplayName("단일 예약 배정 성공 시 예약 객체와 객실 객체 모두 배정 상태로 상호 동기화되어야 한다")
    void verifyBiDirectionalAssignment() {
        Reservation reservation = new Reservation(
                "RSV-001", "홍길동", RoomType.SUPERIOR_TWIN,
                2, "테스트 예약", GuestPreference.empty()
        );

        Optional<Room> assignedRoom = assigner.assign(reservation);

        assertTrue(assignedRoom.isPresent());
        assertTrue(reservation.isAssigned());
        assertTrue(assignedRoom.get().isAssigned());
        assertEquals(assignedRoom.get().getRoomNumber(), reservation.getAssignedRoomNumber());
    }

    @Test
    @DisplayName("특정 타입의 객실이 만실이면 assign은 Optional.empty()를 반환해야 한다")
    void verifyNoRoomAvailableWhenSoldOut() {
        // 슈페리어 트윈 객실 전부 강제 배정 처리
        repository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.SUPERIOR_TWIN)
                .forEach(Room::assign);

        Reservation newBooking = new Reservation(
                "RSV-999", "만실손님", RoomType.SUPERIOR_TWIN,
                1, null, GuestPreference.empty()
        );

        Optional<Room> result = assigner.assign(newBooking);

        assertTrue(result.isEmpty(), "만실 상태에서는 Optional.empty가 반환되어야 합니다.");
        assertFalse(newBooking.isAssigned(), "배정이 실패했으므로 예약 상태는 미배정이어야 합니다.");
    }
}