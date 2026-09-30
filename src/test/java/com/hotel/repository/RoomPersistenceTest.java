package com.hotel.repository;

import com.hotel.domain.Room;
import com.hotel.domain.RoomStatus;
import com.hotel.domain.StayPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class RoomPersistenceTest {

    private static final String ROOM = "0501";
    private static final StayPeriod PERIOD = new StayPeriod(LocalDate.of(2026, 10, 1), 3);

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("[객실 저장] 점검(BLOCKED) 중인 방을 다시 저장해도 예약 스케줄이 지워지지 않아야 한다")
    void blockedRoomKeepsItsSchedules() {
        Room room = roomRepository.findByRoomNumber(ROOM).orElseThrow();
        assertTrue(room.tryBookPeriod(PERIOD));
        roomRepository.save(room);

        room.restoreStatus(RoomStatus.BLOCKED);
        roomRepository.save(room);

        Room reloaded = roomRepository.findByRoomNumber(ROOM).orElseThrow();
        assertEquals(RoomStatus.BLOCKED, reloaded.getStatus());
        assertTrue(reloaded.getBookedPeriods().contains(PERIOD), "점검 중에도 기존 예약 스케줄은 복원되어야 한다");

        // 복원된 객체를 그대로 저장해도 DB의 스케줄이 유지되어야 한다.
        roomRepository.save(reloaded);
        Room again = roomRepository.findByRoomNumberForUpdate(ROOM).orElseThrow();
        assertTrue(again.getBookedPeriods().contains(PERIOD));
    }

    @Test
    @DisplayName("[객실 저장] 전이 규칙상 VACANT에서 갈 수 없는 상태(CLEANING)로 저장된 방도 조회할 수 있어야 한다")
    void roomInCleaningStatusCanBeLoaded() {
        Room room = roomRepository.findByRoomNumber(ROOM).orElseThrow();
        room.restoreStatus(RoomStatus.CLEANING);
        roomRepository.save(room);

        assertEquals(RoomStatus.CLEANING, roomRepository.findByRoomNumber(ROOM).orElseThrow().getStatus());
        assertEquals(191, roomRepository.findAll().size());
    }

    @Test
    @DisplayName("[태그] 객실 단위 태그 추가/삭제는 상태와 스케줄을 건드리지 않는다")
    void tagOperationsLeaveStatusAndScheduleUntouched() {
        Room room = roomRepository.findByRoomNumber(ROOM).orElseThrow();
        assertTrue(room.tryBookPeriod(PERIOD));
        room.restoreStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room);

        assertTrue(roomRepository.addTag(ROOM, "temp_tag"));
        Room withTag = roomRepository.findByRoomNumber(ROOM).orElseThrow();
        assertTrue(withTag.hasTag("TEMP_TAG"));
        assertEquals(RoomStatus.OCCUPIED, withTag.getStatus());
        assertTrue(withTag.getBookedPeriods().contains(PERIOD));

        assertTrue(roomRepository.removeTag(ROOM, "TEMP_TAG"));
        assertEquals(false, roomRepository.findByRoomNumber(ROOM).orElseThrow().hasTag("TEMP_TAG"));
    }

    @Test
    @DisplayName("[태그] 존재하지 않는 객실에는 태그를 붙일 수 없다")
    void tagOnUnknownRoomReturnsFalse() {
        assertEquals(false, roomRepository.addTag("9999", "ANY"));
        assertEquals(false, roomRepository.removeTag("9999", "ANY"));
    }

    @Test
    @DisplayName("[태그] 전체 삭제는 모든 객실에서 해당 태그를 지운다")
    void removeTagFromAllClearsEveryRoom() {
        roomRepository.addTag("0501", "GONE_TAG");
        roomRepository.addTag("0502", "GONE_TAG");

        roomRepository.removeTagFromAll("gone_tag");

        assertTrue(roomRepository.findAll().stream().noneMatch(r -> r.hasTag("GONE_TAG")));
    }
}
