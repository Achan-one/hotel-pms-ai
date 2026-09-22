package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.Reservation;
import com.hotel.service.FloorStatusService;
import com.hotel.service.ReservationService;
import com.hotel.service.dto.FloorMapResponseDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomIndicatorController {

    private final FloorStatusService floorStatusService;
    private final ReservationService reservationService;

    public RoomIndicatorController(FloorStatusService floorStatusService,
                                   ReservationService reservationService) {
        this.floorStatusService = floorStatusService;
        this.reservationService = reservationService;
    }

    /**
     * 191실 층별 실시간 룸 인디케이터 / 룸 랙 매트릭스 조회
     * GET /api/rooms/indicator?targetDate=2026-09-20
     */
    @GetMapping("/indicator")
    public ResponseEntity<ApiResponse<FloorMapResponseDto>> getRoomIndicator(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();
        List<Reservation> allReservations = reservationService.searchReservations(null);

        FloorMapResponseDto matrix = floorStatusService.getFloorMatrix(date, allReservations);
        return ResponseEntity.ok(ApiResponse.ok(matrix));
    }
}