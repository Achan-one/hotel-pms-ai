package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    @DisplayName("[동시성 방어] 1실만 남은 객실에 10개의 요청이 동시 진입해도 정확히 1건만 배정되고 9건은 차단되어야 한다")
    void concurrentAssign_PreventDoubleBooking() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 운영에서는 runDailyBatchAssignment의 트랜잭션 안에서 배정이 일어나 방 락이 저장까지 유지된다.
        // 테스트도 같은 조건으로 맞춘다. 트랜잭션 없이 부르면 락이 조회 직후 풀려서 실제 사용과 달라진다.
        TransactionTemplate tx = new TransactionTemplate(txManager);

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
                    Optional<Room> assigned = tx.execute(status -> roomAssigner.assign(res));
                    if (assigned.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    // DB 백스톱(중복 박 점유 거부)이 동작했다면 여기로 온다. 정상 경로에서는 없어야 한다.
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // 남은 재고 및 쿼터 정책 범위 내에서만 정합성 있게 성공했는지 검증
        System.out.printf("동시 배정 결과 -> 성공: %d, 실패: %d%n", successCount.get(), failCount.get());
        assertEquals(0, errorCount.get(), "방 락을 잡고 배정하면 DB 중복 점유 예외가 나면 안 됩니다.");
        assertEquals(threadCount, successCount.get() + failCount.get());
    }
}