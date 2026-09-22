package com.hotel.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BatchAssignApiRequest(
        @NotNull(message = "체크인 일자는 필수입니다.")
        LocalDate checkInDate
) {}