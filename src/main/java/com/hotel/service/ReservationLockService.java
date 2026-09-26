package com.hotel.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReservationLockService {

    public record LockInfo(String staffId, String staffName, LocalDateTime lockedAt) {}

    private final Map<String, LockInfo> lockStore = new ConcurrentHashMap<>();
    private static final long LOCK_TIMEOUT_MINUTES = 5; // 5분간 갱신 없으면 자동 만료

    /**
     * 편집 락 획득 시도
     */
    public synchronized boolean acquireLock(String reservationId, String staffId, String staffName) {
        cleanExpiredLocks();
        LockInfo current = lockStore.get(reservationId);

        if (current == null) {
            lockStore.put(reservationId, new LockInfo(staffId, staffName, LocalDateTime.now()));
            return true;
        }

        // 본인이 이미 잡은 락이면 시간 갱신
        if (current.staffId().equalsIgnoreCase(staffId)) {
            lockStore.put(reservationId, new LockInfo(staffId, staffName, LocalDateTime.now()));
            return true;
        }

        return false; // 다른 직원이 점유 중
    }

    /**
     * 현재 점유 중인 락 정보 조회
     */
    public Optional<LockInfo> getLockInfo(String reservationId) {
        cleanExpiredLocks();
        return Optional.ofNullable(lockStore.get(reservationId));
    }

    /**
     * 락 해제
     */
    public synchronized void releaseLock(String reservationId, String staffId) {
        LockInfo current = lockStore.get(reservationId);
        if (current != null && current.staffId().equalsIgnoreCase(staffId)) {
            lockStore.remove(reservationId);
        }
    }

    private void cleanExpiredLocks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(LOCK_TIMEOUT_MINUTES);
        lockStore.entrySet().removeIf(entry -> entry.getValue().lockedAt().isBefore(cutoff));
    }
}