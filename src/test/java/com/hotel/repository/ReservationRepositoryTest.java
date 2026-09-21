package com.hotel.repository;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRepositoryTest {

    private ReservationRepository repository;
    private final LocalDate sep20 = LocalDate.of(2026, 9, 20);
    private final LocalDate sep21 = LocalDate.of(2026, 9, 21);

    @BeforeEach
    void setUp() {
        repository = new InMemoryReservationRepository();

        // 테스트 기본 픽스처 4건 적재
        Reservation r1 = new Reservation("RSV-001", "Tanaka Kenji", RoomType.MODERATE_DOUBLE, sep20, 2, "고층 희망", GuestPreference.empty());
        Reservation r2 = new Reservation("RSV-002", "Alice Smith", RoomType.SUPERIOR_TWIN, sep20, 3, "조용한 방", GuestPreference.empty());
        Reservation r3 = new Reservation("RSV-003", "Kim Min-soo", RoomType.EXECUTIVE_DOUBLE, sep21, 1, null, GuestPreference.empty());
        Reservation r4 = new Reservation("RSV-004", "Tanaka Yuto", RoomType.RESIDENTIAL_DOUBLE, sep20, 1, "엘베 근처", GuestPreference.empty());

        // r2는 이미 배정 확정된 상태 시뮬레이션
        r2.assignRoom("0801");

        repository.saveAll(List.of(r1, r2, r3, r4));
    }

    @Test
    @DisplayName("예약 단건 저장 및 ID 조회 정상 동작 검증")
    void saveAndFindById_Success() {
        Optional<Reservation> found = repository.findById("RSV-001");

        assertTrue(found.isPresent());
        assertEquals("Tanaka Kenji", found.get().getGuestName());
        assertEquals(ReservationStatus.PENDING, found.get().getStatus());
    }

    @Test
    @DisplayName("체크인 날짜 기준 전체 예약 조회 검증")
    void findByCheckInDate_Success() {
        List<Reservation> listSep20 = repository.findByCheckInDate(sep20);
        List<Reservation> listSep21 = repository.findByCheckInDate(sep21);

        assertEquals(3, listSep20.size(), "9/20 체크인 예약은 총 3건이어야 합니다.");
        assertEquals(1, listSep21.size(), "9/21 체크인 예약은 총 1건이어야 합니다.");
    }

    @Test
    @DisplayName("특정 체크인 날짜의 미배정(PENDING) 예약만 정확히 필터링 검증")
    void findUnassignedByCheckInDate_Success() {
        List<Reservation> unassignedList = repository.findUnassignedByCheckInDate(sep20);

        assertEquals(2, unassignedList.size());
        assertTrue(unassignedList.stream().allMatch(r -> r.getStatus() == ReservationStatus.PENDING));
        assertTrue(unassignedList.stream().noneMatch(r -> r.getReservationId().equals("RSV-002")));
    }

    @Test
    @DisplayName("고객 이름 부분 일치 및 대소문자 무시 검색 검증")
    void findByGuestName_CaseInsensitive_Contains() {
        List<Reservation> tanakaList = repository.findByGuestName("tanaka");
        List<Reservation> aliceList = repository.findByGuestName("ALICE");

        assertEquals(2, tanakaList.size(), "Tanaka가 포함된 예약은 2건이어야 합니다.");
        assertEquals(1, aliceList.size(), "대문자 ALICE로 검색해도 Alice Smith가 조회되어야 합니다.");
    }

    @Test
    @DisplayName("다조건 동적 검색(search): 체크인 날짜 + 박수 + 상태 복합 조건 필터링 검증")
    void search_ComplexCondition_Success() {
        ReservationSearchCondition condition = new ReservationSearchCondition(
                null, null, sep20, 2, null, ReservationStatus.PENDING, null
        );

        List<Reservation> result = repository.search(condition);

        assertEquals(1, result.size());
        assertEquals("RSV-001", result.get(0).getReservationId());
        assertEquals("Tanaka Kenji", result.get(0).getGuestName());
    }

    @Test
    @DisplayName("예약 라이프사이클 전이 검증: PENDING -> ASSIGNED -> CHECKED_IN (룸체인지 후에도 투숙중 유지) -> CHECKED_OUT")
    void reservationLifecycle_StateTransitions() {
        Reservation res = new Reservation("RSV-LIFE", "Sato", RoomType.SUPERIOR_TWIN, sep20, 2, null, GuestPreference.empty());
        assertEquals(ReservationStatus.PENDING, res.getStatus());

        // 1. 방 배정
        res.assignRoom("0501");
        assertEquals(ReservationStatus.ASSIGNED, res.getStatus());

        // 2. 키 수령 및 입실
        res.checkIn();
        assertEquals(ReservationStatus.CHECKED_IN, res.getStatus());
        assertTrue(res.getStatus().isInHouse());

        // 3. 룸 체인지: 호실 번호만 최신화되고 예약 계약 상태는 CHECKED_IN(투숙중) 유지
        res.changeRoom("0805");
        assertEquals("0805", res.getAssignedRoomNumber());
        assertEquals("0501", res.getPreviousRoomNumber());
        assertEquals(ReservationStatus.CHECKED_IN, res.getStatus(), "룸 체인지 후에도 예약 상태는 투숙중(CHECKED_IN)이어야 합니다.");
        assertTrue(res.getStatus().isInHouse());

        // 4. 퇴실
        res.checkOut();
        assertEquals(ReservationStatus.CHECKED_OUT, res.getStatus());
        assertFalse(res.getStatus().isInHouse());
    }

    @Test
    @DisplayName("[재실 검색] stayingDate 기준으로 해당 날짜에 숙박 중인 연박 고객들을 정확히 조회해야 한다")
    void search_ByStayingDate_Success() {
        ReservationSearchCondition condition = ReservationSearchCondition.byStayingDate(sep21);
        List<Reservation> results = repository.search(condition);

        assertTrue(results.stream().anyMatch(r -> r.getReservationId().equals("RSV-001")));
        assertTrue(results.stream().anyMatch(r -> r.getReservationId().equals("RSV-002")));
    }
}