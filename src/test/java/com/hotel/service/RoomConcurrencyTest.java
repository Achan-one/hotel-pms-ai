package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoomConcurrencyTest {

    private RoomRepository roomRepository;
    private QuotaPolicy quotaPolicy;
    private RoomAssigner assigner;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        roomRepository = new InMemoryRoomRepository();
        quotaPolicy = new QuotaPolicy();
        // 격리 테스트를 위해 이그제큐티브 더블 킵을 0으로 설정
        quotaPolicy.setTypeHoldQuota(RoomType.EXECUTIVE_DOUBLE, 0);
        assigner = new RoomAssigner(roomRepository, quotaPolicy);
    }

    @Test
    @DisplayName("[동시성 스트레스] 잔여 1실에 대해 30개 스레드가 동시 배정 요청 시 정확히 1건만 성공해야 한다 (더블 부킹 방어)")
    void concurrentAssignment_PreventsDoubleBooking() throws InterruptedException {
        // Given: 이그제큐티브 더블 총 4실 중 3실을 사전 점유하여 '단 1실(1404호)'만 공실로 남김
        StayPeriod stay = new StayPeriod(today, 2);
        List<Room> execRooms = roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.EXECUTIVE_DOUBLE)
                .toList();

        for (int i = 0; i < 3; i++) {
            execRooms.get(i).bookPeriod(stay);
        }

        Room targetLastRoom = execRooms.get(3); // 유일한 공실

        int threadCount = 30;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Reservation> assignedReservations = new CopyOnWriteArrayList<>();

        // 30명의 가상 예약 생성
        for (int i = 1; i <= threadCount; i++) {
            final String rsvId = "CONCUR-RSV-" + i;
            final Reservation rsv = new Reservation(
                    rsvId, "Guest_" + i, RoomType.EXECUTIVE_DOUBLE, today, 2, null, GuestPreference.empty()
            );

            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기
                    Optional<Room> assigned = assigner.assign(rsv);
                    if (assigned.isPresent()) {
                        successCount.incrementAndGet();
                        assignedReservations.add(rsv);
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // When: 30개 스레드 동시 일제 사격 트리거
        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then:
        assertTrue(completed, "10초 내에 모든 스레드 작업이 완료되어야 합니다.");
        assertEquals(1, successCount.get(), "오직 1개의 예약만 배정에 성공해야 합니다.");
        assertEquals(29, failureCount.get(), "나머지 29개 예약은 더블 부킹되지 않고 실패해야 합니다.");

        // 배정된 객실의 스케줄이 1건만 등록되었는지 확인
        assertEquals(1, targetLastRoom.getBookedPeriods().size(), "해당 객실의 스케줄 슬롯은 정확히 1개여야 합니다.");
    }
}