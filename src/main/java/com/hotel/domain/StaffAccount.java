package com.hotel.domain;

import java.time.LocalDateTime;

public record StaffAccount(
        String staffId,
        String passwordHash,
        String name,
        StaffRole role,
        boolean enabled,
        int failedAttempts,
        LocalDateTime lockedUntil
) {

    /**
     * 새 계정용. 활성 상태이고 실패 기록이 없다.
     */
    public StaffAccount(String staffId, String passwordHash, String name, StaffRole role) {
        this(staffId, passwordHash, name, role, true, 0, null);
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
