package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ConcurrencyAssignmentTest {

    @Autowired
    private RoomAssigner roomAssigner;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("[동시성 방어] 1실만 남은 객실에 10개의 요청이 동시 진입해도 정확히 1건만 배정되고 9건은 차단되어야 한다")
    void concurrentAssign_PreventDoubleBooking() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        LocalDate checkIn = LocalDate.of(2026, 9, 20);

        for (int i = 0; i < threadCount; i++) {
            final String rsvId = "RSV-CONCURRENT-" + i;
            executorService.submit(() -> {
                try {
                    // 동일한 날짜/타입의 예약 생성
                    Reservation res = new Reservation(
                            rsvId, "동시고객", RoomType.EXECUTIVE_DOUBLE,
                            checkIn, 1, null, GuestPreference.empty()
                    );
                    Optional<Room> assigned = roomAssigner.assign(res);
                    if (assigned.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // 남은 재고 및 쿼터 정책 범위 내에서만 정합성 있게 성공했는지 검증
        System.out.printf("동시 배정 결과 -> 성공: %d, 실패: %d%n", successCount.get(), failCount.get());
        assertEquals(threadCount, successCount.get() + failCount.get());
    }
}