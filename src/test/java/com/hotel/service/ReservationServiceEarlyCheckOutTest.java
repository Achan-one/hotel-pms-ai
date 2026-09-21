package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceEarlyCheckOutTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private ReservationService reservationService;

    // 테스트 기준: 어제(9/20) 체크인, 오늘(9/21) 조기 체크아웃 시뮬레이션
    private final LocalDate yesterday = LocalDate.of(2026, 9, 20);
    private final LocalDate today = LocalDate.of(2026, 9, 21);
    private final LocalDate scheduledCheckOut = LocalDate.of(2026, 9, 23); // 원래 3박 예정일

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        roomRepository = new InMemoryRoomRepository();
        reservationService = new ReservationService(reservationRepository, roomRepository, null);
    }

    @Test
    @DisplayName("[조기 체크아웃] 3박 투숙객이 1박 후 오늘 조기 퇴실 시, 과거 1박은 보존되고 오늘 밤부터의 2박 스케줄은 즉시 회수되어야 한다")
    void earlyCheckOut_FreesFutureSchedule_WhileRetainingPastStay() {
        // Given: 어제(9/20) 체크인하여 9/23까지 3박 예정인 예약 접수 및 입실
        String rsvId = "RSV-EARLY-001";
        PaymentLedger prepaidLedger = new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 450_000); // 정산 완료 상태

        Reservation reservation = new Reservation(
                rsvId, "EarlyGuest", RoomType.SUPERIOR_DOUBLE,
                yesterday, 3, 1, "조용한 방", GuestPreference.empty(),
                null, null, prepaidLedger, null
        );

        reservationService.receiveReservations(List.of(reservation));

        // 9/20 당일 배치 배정 및 체크인 완료 상태 조성
        Room assignedRoom = roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.SUPERIOR_DOUBLE)
                .findFirst()
                .orElseThrow();

        StayPeriod originalStayPeriod = new StayPeriod(yesterday, 3);
        assignedRoom.bookPeriod(originalStayPeriod);
        assignedRoom.setStatus(RoomStatus.OCCUPIED);

        reservation.assignRoom(assignedRoom.getRoomNumber());
        reservation.checkIn();
        reservationRepository.save(reservation);

        // 검증 전 상태 확인: 전체 3박(9/20~9/23) 구간 점유 상태
        assertFalse(assignedRoom.isAvailable(originalStayPeriod));
        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());

        // When: 1박 경과 후 오늘(9/21) 프론트에서 조기 체크아웃 실행
        reservationService.processCheckOut(rsvId);

        // Then 1: 예약 상태는 CHECKED_OUT으로 안전하게 전이되어야 함
        Reservation checkedOutGuest = reservationRepository.findById(rsvId).orElseThrow();
        assertEquals(ReservationStatus.CHECKED_OUT, checkedOutGuest.getStatus());

        // Then 2: 객실의 하우스키핑 상태는 즉시 청소 대기(OUT) 상태로 전이되어야 함
        assertEquals(RoomStatus.OUT, assignedRoom.getStatus(), "퇴실한 객실은 청소 대기(OUT) 상태여야 합니다.");

        // Then 3: [과거 이력 보존] 어제 투숙한 1박 구간([9/20, 9/21))은 객실 이력에 보존되어야 함
        StayPeriod pastStay = new StayPeriod(yesterday, today);
        assertTrue(assignedRoom.getBookedPeriods().stream().anyMatch(p -> p.equals(pastStay)),
                "과거 투숙 이력([9/20, 9/21))은 감사 및 정산을 위해 객실에 보존되어야 합니다.");

        // Then 4: [미래 공실 회수 - 핵심] 오늘 밤부터 예정되어 있던 남은 2박 구간([9/21, 9/23))은 즉시 판매 가능해야 함
        StayPeriod futureRemainingPeriod = new StayPeriod(today, scheduledCheckOut);
        assertTrue(assignedRoom.isAvailable(futureRemainingPeriod),
                "조기 퇴실로 인해 오늘 밤부터의 잔여 2박([9/21, 9/23))은 유령 점유 없이 즉시 공실로 환원되어야 합니다.");
    }
}