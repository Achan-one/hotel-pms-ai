package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    private final LocalDate targetDate = LocalDate.of(2026, 9, 20);

    @Test
    @DisplayName("[동시성 방어] 1개 객실에 10개의 요청이 동시 배정을 시도할 때, 비관적 락(SELECT FOR UPDATE)에 의해 정확히 1건만 배정되고 9건은 거절되어야 한다")
    void concurrentManualAssign_ExactOneWins() throws InterruptedException {
        int threadCount = 10;
        String targetRoomNumber = "0305";

        // 10건의 미배정 예약 시드 생성
        List<String> rsvIds = new ArrayList<>();
        for (int i = 1; i <= threadCount; i++) {
            String id = "RACE-RSV-" + i;
            rsvIds.add(id);
            reservationRepository.save(new Reservation(
                    id, "Racer " + i, RoomType.MODERATE_DOUBLE,
                    targetDate, 2, null, GuestPreference.empty()
            ));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (String rsvId : rsvIds) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 10개 스레드 동시 출발 대기
                    reservationService.manualAssignRoom(rsvId, targetRoomNumber);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 출발 트리거
        endLatch.await();
        executorService.shutdown();

        assertEquals(1, successCount.get(), "오버부킹 없이 정확히 1건의 예약만 배정에 성공해야 합니다.");
        assertEquals(9, failCount.get(), "경합에서 밀린 9건은 락 대기 후 점유 검증에 걸려 예외 처리되어야 합니다.");
    }
}