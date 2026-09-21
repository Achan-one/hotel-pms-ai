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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAssignerTest {

    private RoomRepository repository;
    private BatchAssigner batchAssigner;

    @BeforeEach
    void setUp() {
        repository = new com.hotel.repository.memory.InMemoryRoomRepository();
        RoomAssigner roomAssigner = new RoomAssigner(repository);
        batchAssigner = new BatchAssigner(roomAssigner);
    }

    @Test
    @DisplayName("연박 일수가 길고 요구 조건이 많은 예약이 단기/단순 예약보다 먼저 배정되어야 한다")
    void verifyPriorityOrdering() {
        // 단기 단순 예약 (1박, 조건 없음) - 리스트 앞에 배치
        Reservation shortStay = new Reservation(
                "RSV-SHORT", "단기손님", RoomType.MODERATE_DOUBLE,
                1, null, GuestPreference.empty()
        );

        // 장기 까다로운 예약 (5박, 고층+엘베이격+코너+조용함) - 리스트 뒤에 배치
        GuestPreference strictPref = new GuestPreference(
                FloorPref.HIGH,
                ElevatorPref.AWAY,
                CornerPref.PREFER,
                true
        );
        Reservation longStay = new Reservation(
                "RSV-LONG", "장기손님", RoomType.MODERATE_DOUBLE,
                5, null, strictPref
        );

        // 입력 순서는 shortStay가 먼저이지만, BatchAssigner 내부에서 longStay가 우선 배정되어야 함
        BatchAssignmentResult result = batchAssigner.assignAll(List.of(shortStay, longStay));

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());

        // 장기 투숙객이 모더레이트 더블 최고 선호 객실(1316호 등 고층 코너 이격 객실)을 선점했는지 검증
        Room assignedRoomForLongStay = repository.findByRoomNumber(longStay.getAssignedRoomNumber()).orElseThrow();
        assertTrue(assignedRoomForLongStay.getFloor() >= 10, "장기 고객에게 고층이 우선 배정되어야 합니다.");
        assertTrue(assignedRoomForLongStay.isCorner(), "장기 고객에게 코너룸이 우선 배정되어야 합니다.");
    }
}