package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.api.dto.BatchAssignApiRequest;
import com.hotel.api.dto.RoomChangeApiRequest;
import com.hotel.domain.Reservation;
import com.hotel.service.BatchAssignmentResult; // [수정] com.hotel.service 패키지로 변경
import com.hotel.service.ReservationService;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 특정 체크인 일자의 미배정 예약 일괄 객실 배정
     */
    @PostMapping("/batch-assign")
    public ResponseEntity<ApiResponse<BatchAssignmentResult>> batchAssign(
            @Valid @RequestBody BatchAssignApiRequest request) {
        BatchAssignmentResult result = reservationService.runDailyBatchAssignment(request.checkInDate());
        return ResponseEntity.ok(ApiResponse.ok("일괄 배정이 완료되었습니다.", result));
    }

    /**
     * 단일 예약 상세 조회
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Reservation>> getReservation(
            @PathVariable String reservationId) {
        return reservationService.getReservation(reservationId)
                .map(reservation -> ResponseEntity.ok(ApiResponse.ok(reservation)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.fail("해당 예약을 찾을 수 없습니다: " + reservationId)));
    }

    /**
     * 체크인 실행 (객실 물리 상태 OCCUPIED 동기화 포함)
     */
    @PostMapping("/{reservationId}/check-in")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @PathVariable String reservationId) {
        try {
            reservationService.processCheckIn(reservationId);
            return ResponseEntity.ok(ApiResponse.ok("체크인이 완료되었습니다.", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
        }
    }

    /**
     * 룸 체인지 실행
     */
    @PostMapping("/{reservationId}/room-change")
    public ResponseEntity<ApiResponse<RoomChangeResult>> roomChange(
            @PathVariable String reservationId,
            @Valid @RequestBody RoomChangeApiRequest request) {
        try {
            LocalDate moveDate = (request.moveDate() != null) ? request.moveDate() : LocalDate.now();
            String reason = (request.reason() != null && !request.reason().isBlank()) ? request.reason() : "프론트 현장 요청";

            RoomChangeRequest domainRequest = new RoomChangeRequest(
                    reservationId,
                    request.targetRoomNumber(),
                    moveDate,
                    reason
            );

            RoomChangeResult result = reservationService.processRoomChange(domainRequest);
            if (result.success()) {
                return ResponseEntity.ok(ApiResponse.ok("룸 체인지가 완료되었습니다.", result));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.fail(result.message()));
            }
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
        }
    }

    /**
     * 체크아웃 실행 (객실 물리 상태 OUT 전환 포함)
     */
    @PostMapping("/{reservationId}/check-out")
    public ResponseEntity<ApiResponse<Void>> checkOut(
            @PathVariable String reservationId,
            @RequestParam(required = false) LocalDate checkOutDate) {
        try {
            LocalDate effectiveDate = (checkOutDate != null) ? checkOutDate : LocalDate.now();
            reservationService.processCheckOut(reservationId, effectiveDate);
            return ResponseEntity.ok(ApiResponse.ok("퇴실 처리가 완료되었습니다.", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
        }
    }
}