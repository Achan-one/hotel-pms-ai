package com.hotel.api.dto;

import com.hotel.domain.StaffRole;

public record LoginResponse(
        String token,
        String tokenType,
        String staffId,
        String staffName,
        StaffRole role,
        String roleDescription
) {
    public static LoginResponse of(String token, String staffId, String staffName, StaffRole role) {
        return new LoginResponse(token, "Bearer", staffId, staffName, role, role.getDescription());
    }
}