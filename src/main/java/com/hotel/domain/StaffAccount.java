package com.hotel.domain;

public record StaffAccount(
        String staffId,
        String passwordHash,
        String name,
        StaffRole role
) {}