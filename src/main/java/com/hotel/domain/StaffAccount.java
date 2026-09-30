package com.hotel.domain;

public record StaffAccount(
        String staffId,
        String passwordHash,
        String name,
        StaffRole role,
        boolean enabled
) {

    /**
     * 새 계정용. 활성 상태로 만든다.
     */
    public StaffAccount(String staffId, String passwordHash, String name, StaffRole role) {
        this(staffId, passwordHash, name, role, true);
    }
}
