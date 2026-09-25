package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.Room;
import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.domain.TagPreference;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationServiceTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private ReservationService reservationService;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        roomRepository = new InMemoryRoomRepository();
        TagRepository tagRepository = new InMemoryTagRepository();
        QuotaPolicy quotaPolicy = new QuotaPolicy();

        // [태그 지향 모의 AI 스텁] 요청 텍스트에 "고층"이 있으면 HIGH_FLOOR 태그 스위치를 켬
        AiPreferenceParser stubAiParser = new AiPreferenceParser(tagRepository, null, null) {
            @Override
            public Map<String, TagPreference> parseBatch(List<Reservation> reservations) {
                Map<String, TagPreference> map = new HashMap<>();
                for (Reservation r : reservations) {
                    if (r.getRawRequestText() != null && r.getRawRequestText().contains("고층")) {
                        map.put(r.getReservationId(), new TagPreference(Set.of("HIGH_FLOOR"), Set.of()));
                    } else {
                        map.put(r.getReservationId(), TagPreference.empty());
                    }
                }
                return map;
            }
        };

        // 5개 인자 마스터 생성자에 맞추어 의존성 주입
        reservationService = new ReservationService(
                reservationRepository,
                roomRepository,
                stubAiParser,
                tagRepository,
                quotaPolicy
        );
    }

    @Test
    @DisplayName("[1. 예약 접수] 유효한 예약만 PENDING 상태로 장부에 정상 저장되어야 한다")
    void receiveReservations_Success() {
        Reservation valid1 = new Reservation("RSV-001", "Tanaka", RoomType.MODERATE_DOUBLE, today, 2, "고층 희망", GuestPreference.empty());
        Reservation valid2 = new Reservation("RSV-002", "Alice", RoomType.SUPERIOR_TWIN, today, 3, "조용한 방", GuestPreference.empty());
        Reservation invalid = new Reservation("RSV-003", "Bob", RoomType.SUPERIOR_TWIN, today, 32, "초과", GuestPreference.empty());

        List<Reservation> received = reservationService.receiveReservations(List.of(valid1, valid2, invalid));

        assertEquals(2, received.size());
        assertEquals(2, reservationRepository.count());

        Reservation saved = reservationRepository.findById("RSV-001").orElseThrow();
        assertEquals(ReservationStatus.PENDING, saved.getStatus());
        assertNull(saved.getAssignedRoomNumber());
    }

    @Test
    @DisplayName("[2. 당일 자동 배정] PENDING 예약이 AI 선호도 주입 후 ASSIGNED 상태로 배정 확정되어야 한다")
    void runDailyBatchAssignment_Success() {
        Reservation r1 = new Reservation("RSV-BATCH-01", "Guest1", RoomType.MODERATE_DOUBLE, today, 2, "고층", GuestPreference.empty());
        Reservation r2 = new Reservation("RSV-BATCH-02", "Guest2", RoomType.SUPERIOR_TWIN, today, 3, "조용한 방", GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1, r2));

        BatchAssignmentResult result = reservationService.runDailyBatchAssignment(today);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());

        Reservation assignedR1 = reservationRepository.findById("RSV-BATCH-01").orElseThrow();
        assertEquals(ReservationStatus.ASSIGNED, assignedR1.getStatus());
        assertNotNull(assignedR1.getAssignedRoomNumber());
        assertTrue(assignedR1.isAssigned());

        assertTrue(assignedR1.getTagPreference().preferredTags().contains("HIGH_FLOOR"));
    }

    @Test
    @DisplayName("[3. 체크인 처리] 배정 확정된 예약은 고객 키 수령 시 CHECKED_IN(투숙중)으로 전환되어야 한다")
    void processCheckIn_Success() {
        Reservation r1 = new Reservation("RSV-CHECKIN-01", "Tanaka", RoomType.MODERATE_DOUBLE, today, 2, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);

        reservationService.processCheckIn("RSV-CHECKIN-01");

        Reservation inHouseGuest = reservationRepository.findById("RSV-CHECKIN-01").orElseThrow();
        assertEquals(ReservationStatus.CHECKED_IN, inHouseGuest.getStatus());
        assertTrue(inHouseGuest.getStatus().isInHouse());
    }

    @Test
    @DisplayName("[3-1. 체크인 방어] 미배정(PENDING) 상태의 예약은 체크인을 시도하면 예외가 발생해야 한다")
    void processCheckIn_Unassigned_ThrowsException() {
        Reservation unassigned = new Reservation("RSV-FAIL-01", "FailGuest", RoomType.MODERATE_DOUBLE, today, 1, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(unassigned));

        assertThrows(IllegalStateException.class, () ->
                reservationService.processCheckIn("RSV-FAIL-01"), "미배정 예약은 체크인할 수 없습니다.");
    }

    @Test
    @DisplayName("[4. 룸 체인지] 동일 타입 공실로 이동 성공 시 배정 호실이 변경되고 예약 상태는 CHECKED_IN(투숙중)을 유지해야 한다")
    void processRoomChange_Success() {
        Reservation r1 = new Reservation("RSV-MOVE-01", "Kim", RoomType.SUPERIOR_TWIN, today, 2, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);
        reservationService.processCheckIn("RSV-MOVE-01");

        Reservation beforeMove = reservationRepository.findById("RSV-MOVE-01").orElseThrow();
        String originRoom = beforeMove.getAssignedRoomNumber();

        StayPeriod period = new StayPeriod(today, 2);
        Room targetRoom = roomRepository.findAll().stream()
                .filter(room -> room.getRoomType() == RoomType.SUPERIOR_TWIN)
                .filter(room -> !room.getRoomNumber().equals(originRoom))
                .filter(room -> room.getStatus().isAssignable())
                .filter(room -> room.isAvailable(period))
                .findFirst()
                .orElseThrow();

        // 서비스 정식 DTO 규약인 RoomChangeRequest 객체를 생성하여 전달
        RoomChangeRequest changeRequest = new RoomChangeRequest(
                "RSV-MOVE-01",
                targetRoom.getRoomNumber(),
                today,
                "현장 프론트 요청"
        );
        RoomChangeResult result = reservationService.processRoomChange(changeRequest);

        assertTrue(result.success());
        Reservation movedReservation = reservationRepository.findById("RSV-MOVE-01").orElseThrow();

        assertEquals(ReservationStatus.CHECKED_IN, movedReservation.getStatus(), "룸 체인지 후에도 예약 상태는 투숙중(CHECKED_IN)이어야 합니다.");
        assertEquals(targetRoom.getRoomNumber(), movedReservation.getAssignedRoomNumber());
        assertEquals(originRoom, movedReservation.getPreviousRoomNumber());
        assertTrue(movedReservation.getStatus().isInHouse());
    }

    @Test
    @DisplayName("[5. 체크아웃 처리] 투숙 중 고객은 프론트 정산 완료 후 정상 퇴실(CHECKED_OUT)되며 객실은 OUT 상태로 전이된다")
    void processCheckOut_Success() {
        Reservation r1 = new Reservation("RSV-OUT-01", "Lee", RoomType.MODERATE_DOUBLE, today, 1, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);
        reservationService.processCheckIn("RSV-OUT-01");

        Reservation checkedIn = reservationRepository.findById("RSV-OUT-01").orElseThrow();
        String assignedRoom = checkedIn.getAssignedRoomNumber();

        r1.getPaymentLedger().settle();
        reservationService.processCheckOut("RSV-OUT-01", today);

        Reservation checkedOut = reservationRepository.findById("RSV-OUT-01").orElseThrow();
        assertEquals(ReservationStatus.CHECKED_OUT, checkedOut.getStatus());
        assertFalse(checkedOut.getStatus().isInHouse());

        Room room = roomRepository.findByRoomNumber(assignedRoom).orElseThrow();
        assertEquals(RoomStatus.OUT, room.getStatus(), "체크아웃된 객실은 청소 대기(OUT) 상태여야 합니다.");
    }

    @Test
    @DisplayName("[6. 통합 검색] 상태 및 체크인 일자 복합 검색으로 대상 예약을 정확히 필터링해야 한다")
    void searchReservations_Success() {
        Reservation r1 = new Reservation("RSV-SEARCH-01", "Tanaka Kenji", RoomType.MODERATE_DOUBLE, today, 1, null, GuestPreference.empty());
        Reservation r2 = new Reservation("RSV-SEARCH-02", "Alice Smith", RoomType.SUPERIOR_TWIN, today, 2, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1, r2));

        reservationService.runDailyBatchAssignment(today);
        reservationService.processCheckIn("RSV-SEARCH-01");

        ReservationSearchCondition condition = new ReservationSearchCondition(
                null, null, today, null, null, null, ReservationStatus.CHECKED_IN, null, null,null
        );

        List<Reservation> results = reservationService.searchReservations(condition);

        assertEquals(1, results.size());
        assertEquals("RSV-SEARCH-01", results.getFirst().getReservationId());
        assertEquals("Tanaka Kenji", results.getFirst().getGuestName());
    }

    @Test
    @DisplayName("[배정 취소] 배정 완료된 예약의 배정을 취소하면 객실 스케줄이 즉시 반납되어 재배정 가능해진다")
    void cancelRoomAssignment_Success() {
        Reservation r1 = new Reservation("RSV-CANCEL-ASSIGN", "Yamada", RoomType.MODERATE_DOUBLE, today, 2, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);

        Reservation assigned = reservationRepository.findById("RSV-CANCEL-ASSIGN").orElseThrow();
        String roomNumber = assigned.getAssignedRoomNumber();
        Room room = roomRepository.findByRoomNumber(roomNumber).orElseThrow();

        StayPeriod stayPeriod = new StayPeriod(today, 2);
        assertFalse(room.isAvailable(stayPeriod));

        reservationService.cancelRoomAssignment("RSV-CANCEL-ASSIGN");

        Reservation unassigned = reservationRepository.findById("RSV-CANCEL-ASSIGN").orElseThrow();
        assertEquals(ReservationStatus.PENDING, unassigned.getStatus());
        assertNull(unassigned.getAssignedRoomNumber());
        assertTrue(room.isAvailable(stayPeriod));
    }

    @Test
    @DisplayName("[예약 취소] 배정 상태에서 고객이 예약을 취소하면 객실 스케줄이 회수되고 CANCELLED 상태로 전환된다")
    void cancelReservation_Success() {
        Reservation r1 = new Reservation("RSV-CANCEL-RES", "Suzuki", RoomType.SUPERIOR_TWIN, today, 3, null, GuestPreference.empty());
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);

        Reservation assigned = reservationRepository.findById("RSV-CANCEL-RES").orElseThrow();
        String roomNumber = assigned.getAssignedRoomNumber();
        Room room = roomRepository.findByRoomNumber(roomNumber).orElseThrow();

        StayPeriod stayPeriod = new StayPeriod(today, 3);
        assertFalse(room.isAvailable(stayPeriod));

        reservationService.cancelReservation("RSV-CANCEL-RES");

        Reservation cancelled = reservationRepository.findById("RSV-CANCEL-RES").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertTrue(room.isAvailable(stayPeriod));
    }

    @Test
    @DisplayName("[현장 운영 태그 오버라이드] 현장에서 태그 선호도를 수정해도 OTA 원본 요청 메모는 불변 보존되어야 한다")
    void updateOperationalTags_RetainsOriginalContractData() {
        String rawMemo = "고층 전망 희망합니다.";
        Reservation reservation = new Reservation(
                "RSV-OVERRIDE-01", "Tanaka", RoomType.MODERATE_DOUBLE,
                today, 2, rawMemo, GuestPreference.empty()
        );
        reservationService.receiveReservations(List.of(reservation));

        Set<String> newPreferred = Set.of("LOW_FLOOR", "NEAR_ELEVATOR");
        Set<String> newAvoid = Set.of("HIGH_FLOOR");
        reservationService.updateOperationalTags("RSV-OVERRIDE-01", newPreferred, newAvoid);

        Reservation updated = reservationRepository.findById("RSV-OVERRIDE-01").orElseThrow();

        assertEquals(newPreferred, updated.getTagPreference().preferredTags());
        assertEquals(newAvoid, updated.getTagPreference().avoidTags());

        assertEquals(rawMemo, updated.getRawRequestText(), "OTA 원본 요청 메모는 절대 오염되지 않아야 합니다.");
        assertEquals("Tanaka", updated.getOriginalGuestName());
        assertEquals(today, updated.getContractCheckInDate());
        assertEquals(2, updated.getContractStayNights());
    }
}