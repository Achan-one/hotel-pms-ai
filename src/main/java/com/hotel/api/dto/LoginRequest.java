package com.hotel.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "직원 ID는 필수입니다.")
        String staffId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {}