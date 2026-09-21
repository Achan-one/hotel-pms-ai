package com.hotel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomStatusStateMachineTest {

    @Test
    @DisplayName("[상태 머신 방어] 손님이 투숙 중인 OCCUPIED 객실을 청소(OUT) 없이 공실(VACANT)로 건너뛰면 예외가 터져야 한다")
    void occupied_CannotDirectlyTransitionToVacant() {
        Room room = new Room("1001", 10, RoomType.MODERATE_DOUBLE, false, false);
        room.setStatus(RoomStatus.OCCUPIED);

        // OCCUPIED -> VACANT 직접 전이 차단 검증
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                room.setStatus(RoomStatus.VACANT));

        assertTrue(ex.getMessage().contains("허용되지 않는 상태 전이"));
    }

    @Test
    @DisplayName("[정상 라이프사이클 전이] OCCUPIED -> OUT -> CLEANING -> VACANT 전이는 순차적으로 통과해야 한다")
    void normalHousekeepingLifecycle_Success() {
        Room room = new Room("1001", 10, RoomType.MODERATE_DOUBLE, false, false);
        room.setStatus(RoomStatus.OCCUPIED);

        // 1. 체크아웃 (OUT)
        assertDoesNotThrow(room::markCheckOut);
        assertEquals(RoomStatus.OUT, room.getStatus());

        // 2. 청소 시작 (CLEANING)
        assertDoesNotThrow(room::startCleaning);
        assertEquals(RoomStatus.CLEANING, room.getStatus());

        // 3. 청소 완료 점검 (VACANT)
        assertDoesNotThrow(room::finishCleaning);
        assertEquals(RoomStatus.VACANT, room.getStatus());
        assertTrue(room.getStatus().isAssignable());
    }
}