package com.hotel.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 일괄 자동 배정, 일괄 해제가 도는 동안 다른 요청이 예약을 건드리지 못하게 하는 전역 잠금.
 * 서버 한 대 기준의 메모리 잠금이라 ReservationLockService와 같은 전제다. 여러 대로 늘리면 DB나 공유 저장소로 옮겨야 한다.
 * 조회는 막지 않는다. 상태는 {@link #current()}로 알 수 있어 프론트가 읽기 전용 화면으로 전환하는 데 쓴다.
 */
@Component
public class BatchOperationGuard {

    /** 요청이 비정상 종료돼도 잠금이 영원히 남지 않도록 두는 상한. AI 호출 제한시간(45초)보다 충분히 길다. */
    static final Duration MAX_HOLD = Duration.ofMinutes(10);

    public record Active(String operation, String label, String staffId, LocalDate targetDate, Instant startedAt) {
        public String describe() {
            return String.format("%s이(가) 진행 중입니다 (실행: %s, 대상 일자: %s).", label, staffId, targetDate);
        }
    }

    private final AtomicReference<Active> current = new AtomicReference<>();

    /**
     * 잠금을 잡고 작업을 실행한 뒤 반드시 놓는다. 이미 다른 일괄 작업이 진행 중이면 실행하지 않고 예외를 던진다.
     */
    public <T> T runExclusive(String operation, String label, String staffId, LocalDate targetDate, Supplier<T> body) {
        Active mine = new Active(operation, label, staffId, targetDate, Instant.now());
        acquire(mine);
        try {
            return body.get();
        } finally {
            current.compareAndSet(mine, null);
        }
    }

    public Optional<Active> current() {
        Active active = current.get();
        if (active == null || isExpired(active)) {
            return Optional.empty();
        }
        return Optional.of(active);
    }

    private void acquire(Active mine) {
        while (true) {
            Active existing = current.get();
            if (existing != null && !isExpired(existing)) {
                throw new BatchInProgressException(existing.describe() + " 끝난 뒤 다시 시도해 주세요.");
            }
            if (current.compareAndSet(existing, mine)) {
                return;
            }
        }
    }

    private boolean isExpired(Active active) {
        return Duration.between(active.startedAt(), Instant.now()).compareTo(MAX_HOLD) > 0;
    }
}
