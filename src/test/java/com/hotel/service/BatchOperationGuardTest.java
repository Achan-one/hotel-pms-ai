package com.hotel.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchOperationGuardTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    private final BatchOperationGuard guard = new BatchOperationGuard();

    @Test
    @DisplayName("[일괄 잠금] 실행하는 동안에만 진행 중으로 보이고 끝나면 풀린다")
    void activeOnlyWhileRunning() {
        assertTrue(guard.current().isEmpty());

        String result = guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "staff1", DAY, () -> {
            var active = guard.current().orElseThrow();
            assertEquals("BATCH_ASSIGN", active.operation());
            assertEquals("staff1", active.staffId());
            assertEquals(DAY, active.targetDate());
            return "done";
        });

        assertEquals("done", result);
        assertTrue(guard.current().isEmpty());
    }

    @Test
    @DisplayName("[일괄 잠금] 작업이 예외로 끝나도 잠금이 반드시 풀린다")
    void releasedEvenWhenBodyFails() {
        assertThrows(IllegalStateException.class, () ->
                guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "staff1", DAY, () -> {
                    throw new IllegalStateException("배정 중 오류");
                }));

        assertTrue(guard.current().isEmpty(), "실패한 뒤에도 잠금이 남으면 예약이 영원히 조회 전용이 된다");
    }

    @Test
    @DisplayName("[일괄 잠금] 이미 진행 중이면 두 번째 작업은 실행되지 않고 거절된다")
    void secondOperationIsRejectedWithoutRunning() {
        AtomicBoolean secondRan = new AtomicBoolean(false);

        guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "staff1", DAY, () -> {
            BatchInProgressException ex = assertThrows(BatchInProgressException.class, () ->
                    guard.runExclusive("BATCH_UNASSIGN", "일괄 배정 해제", "staff2", DAY, () -> {
                        secondRan.set(true);
                        return null;
                    }));
            assertTrue(ex.getMessage().contains("AI 일괄 자동 배정"));
            assertTrue(ex.getMessage().contains("staff1"));
            return null;
        });

        assertFalse(secondRan.get());
        assertTrue(guard.current().isEmpty());
    }

    @Test
    @DisplayName("[일괄 잠금] 끝난 뒤에는 다음 작업을 다시 시작할 수 있다")
    void canRunAgainAfterFinish() {
        guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "staff1", DAY, () -> null);

        assertEquals("ok", guard.runExclusive("BATCH_UNASSIGN", "일괄 배정 해제", "staff2", DAY, () -> "ok"));
    }
}
