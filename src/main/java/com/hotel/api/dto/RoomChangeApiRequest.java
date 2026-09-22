package com.hotel.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record RoomChangeApiRequest(
        @NotBlank(message = "목표 호실 번호는 필수입니다.")
        String targetRoomNumber,
        LocalDate moveDate,
        String reason
) {}