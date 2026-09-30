package com.hotel.repository;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ReservationRestoreTest {

    private static final LocalDate DAY = LocalDate.of(2027, 5, 1);

    @Autowired
    private ReservationRepository repository;

    @AfterEach
    void cleanUp() {
        repository.clear();
    }

    private Reservation newReservation(String id) {
        return new Reservation(id, "Tanaka", RoomType.SUPERIOR_TWIN, DAY, 3, null, GuestPreference.empty());
    }

    @Test
    @DisplayName("[복원] 룸체인지한 예약을 다시 읽어도 이전 객실 기록이 유지된다")
    void previousRoomSurvivesReloadAndResave() {
        Reservation r = newReservation("RSV-RESTORE-1");
        r.assignRoom("0501");
        r.checkIn();
        r.changeRoom("0805");
        repository.save(r);

        Reservation loaded = repository.findById("RSV-RESTORE-1").orElseThrow();
        assertEquals("0501", loaded.getPreviousRoomNumber());
        assertEquals("0805", loaded.getAssignedRoomNumber());

        // 다시 저장하고 읽어도 이력이 덮어써지지 않는다.
        repository.save(loaded);
        assertEquals("0501", repository.findById("RSV-RESTORE-1").orElseThrow().getPreviousRoomNumber());
    }

    @Test
    @DisplayName("[복원] 퇴실 후 뒤늦게 요금이 추가되어도 예약을 읽을 수 있고 목록 조회도 죽지 않는다")
    void chargeAfterCheckOutDoesNotBreakLoading() {
        Reservation r = newReservation("RSV-RESTORE-2");
        r.assignRoom("0502");
        r.checkIn();
        r.checkOut(DAY.plusDays(1));
        repository.save(r);

        r.getPaymentLedger().addCharge("MINIBAR", "체크아웃 후 발견된 미니바", 3_000L);
        repository.save(r);

        Reservation loaded = assertDoesNotThrow(() -> repository.findById("RSV-RESTORE-2").orElseThrow());
        assertEquals(ReservationStatus.CHECKED_OUT, loaded.getStatus());
        assertEquals(DAY.plusDays(1), loaded.getActualCheckOutDate());
        assertEquals(3_000L, loaded.getPaymentLedger().getBalance());
        assertEquals(1, assertDoesNotThrow(() -> repository.search(null)).size());
    }

    @Test
    @DisplayName("[복원] 모든 예약 상태가 저장 후에도 같은 상태와 객실로 복원된다")
    void everyStatusRoundTrips() {
        Reservation pending = newReservation("RSV-ST-PENDING");

        Reservation assigned = newReservation("RSV-ST-ASSIGNED");
        assigned.assignRoom("0503");

        Reservation inHouse = newReservation("RSV-ST-INHOUSE");
        inHouse.assignRoom("0504");
        inHouse.checkIn();

        Reservation out = newReservation("RSV-ST-OUT");
        out.assignRoom("0505");
        out.checkIn();
        out.checkOut(DAY.plusDays(2));

        Reservation cancelled = newReservation("RSV-ST-CANCELLED");
        cancelled.assignRoom("0506");
        cancelled.cancelReservation();

        repository.saveAll(java.util.List.of(pending, assigned, inHouse, out, cancelled));

        assertEquals(ReservationStatus.PENDING, repository.findById("RSV-ST-PENDING").orElseThrow().getStatus());
        Reservation a = repository.findById("RSV-ST-ASSIGNED").orElseThrow();
        assertEquals(ReservationStatus.ASSIGNED, a.getStatus());
        assertEquals("0503", a.getAssignedRoomNumber());
        assertEquals(ReservationStatus.CHECKED_IN, repository.findById("RSV-ST-INHOUSE").orElseThrow().getStatus());
        Reservation o = repository.findById("RSV-ST-OUT").orElseThrow();
        assertEquals(ReservationStatus.CHECKED_OUT, o.getStatus());
        assertEquals(DAY.plusDays(2), o.getActualCheckOutDate());
        Reservation c = repository.findById("RSV-ST-CANCELLED").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, c.getStatus());
        assertNull(c.getAssignedRoomNumber());
    }

    @Test
    @DisplayName("[낙관적 락] withTagPreference로 만든 복제본도 원본의 버전을 들고 있어야 한다")
    void tagPreferenceCloneKeepsVersion() {
        Reservation r = newReservation("RSV-CLONE-1");
        repository.save(r);
        Reservation loaded = repository.findById("RSV-CLONE-1").orElseThrow();
        assertNotNull(loaded.getVersion());

        Reservation clone = loaded.withTagPreference(new TagPreference(Set.of("HIGH_FLOOR"), Set.of()));

        assertEquals(loaded.getVersion(), clone.getVersion());
    }

    @Test
    @DisplayName("[낙관적 락] 복제본으로 저장해도 그 사이 다른 직원이 바꾼 내용을 덮어쓰지 못한다")
    void staleCloneCannotOverwriteConcurrentEdit() {
        repository.save(newReservation("RSV-CLONE-2"));
        Reservation stale = repository.findById("RSV-CLONE-2").orElseThrow();
        Reservation clone = stale.withTagPreference(new TagPreference(Set.of("HIGH_FLOOR"), Set.of()));

        Reservation other = repository.findById("RSV-CLONE-2").orElseThrow();
        other.updateOperationalDetails("다른 직원 수정", null, null, null);
        repository.save(other);

        assertThrows(OptimisticLockingFailureException.class, () -> repository.save(clone));
        assertEquals("다른 직원 수정", repository.findById("RSV-CLONE-2").orElseThrow().getOperationalGuestName());
    }

    @Test
    @DisplayName("[낙관적 락] saveAll에서도 버전 없는 새 객체는 기존 예약을 덮어쓰고, 나머지는 그대로 저장된다")
    void saveAllHandlesMixOfNewAndExisting() {
        repository.save(newReservation("RSV-MIX-1"));

        Reservation replacement = new Reservation("RSV-MIX-1", "Replaced", RoomType.SUPERIOR_TWIN, DAY, 2, null, GuestPreference.empty());
        Reservation brandNew = newReservation("RSV-MIX-2");
        repository.saveAll(java.util.List.of(replacement, brandNew));

        assertEquals("Replaced", repository.findById("RSV-MIX-1").orElseThrow().getOriginalGuestName());
        assertTrue(repository.findById("RSV-MIX-2").isPresent());
        assertEquals(2, repository.count());
    }
}
