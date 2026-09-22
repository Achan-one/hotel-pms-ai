package com.hotel.domain;

public enum StaffRole {
    ROLE_ADMIN("호텔 관리자"),
    ROLE_STAFF("정직원 (사원)"),
    ROLE_PART_TIME("아르바이트"),
    ROLE_GUEST("게스트 (고객)");

    private final String description;

    StaffRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}