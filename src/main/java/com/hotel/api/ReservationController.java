package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.api.dto.BatchAssignApiRequest;
import com.hotel.api.dto.RoomChangeApiRequest;
import com.hotel.domain.Reservation;
import com.hotel.service.BatchAssignmentResult;
import com.hotel.service.NightAuditService;
import com.hotel.service.ReservationService;
import com.hotel.service.TestDataGeneratorService;
import com.hotel.service.dto.NightAuditResult;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final NightAuditService nightAuditService;
    private final TestDataGeneratorService testDataGeneratorService;

    public ReservationController(ReservationService reservationService,
                                 NightAuditService nightAuditService,
                                 TestDataGeneratorService testDataGeneratorService) {
        this.reservationService = reservationService;
        this.nightAuditService = nightAuditService;
        this.testDataGeneratorService = testDataGeneratorService;
    }

    public record TagOverrideApiRequest(
            Set<String> preferredTags,
            Set<String> avoidTags
    ) {}

    public record UpdateDailyRatesRequest(
            Map<String, Long> dailyRates
    ) {}

    public record FolioTransactionApiRequest(
            String type,                    // "PAYMENT" 또는 "CHARGE"
            String paymentMethod,           // "CREDIT_CARD", "CASH" 등
            String category,                // "MINIBAR", "DAMAGE", "EXTRA_BED" 등
            String description,             // 메모 및 승인번호
            long amount,                    // 금액
            String instantChargeCategory,   // 동시 분개 시 사유 (선택 시 ±0 처리)
            String instantChargeDescription // 동시 분개 상세
    ) {}

    // 🚀 [신규] 예약별 원장 수납/청구 거래 등록 API (복식 분개 지원)
    @PostMapping("/{reservationId}/folio/transactions")
    public ResponseEntity<ApiResponse<Void>> addFolioTransaction(
            @PathVariable String reservationId,
            @RequestBody FolioTransactionApiRequest request) {

        reservationService.addFolioTransaction(
                reservationId,
                request.type(),
                request.paymentMethod(),
                request.category(),
                request.description(),
                request.amount(),
                request.instantChargeCategory(),
                request.instantChargeDescription()
        );

        return ResponseEntity.ok(ApiResponse.ok("원장 거래 내역이 정상적으로 등록되었습니다.", null));
    }

    // 예약 일자별 1박 요금 스케줄 일괄 갱신 API
    @PutMapping("/{reservationId}/daily-rates")
    public ResponseEntity<ApiResponse<Void>> updateDailyRates(
            @PathVariable String reservationId,
            @RequestBody UpdateDailyRatesRequest request) {

        Reservation reservation = reservationService.getReservation(reservationId)
                .orElseThrow(() -> new NoSuchElementException("해당 예약을 찾을 수 없습니다: " + reservationId));

        Map<LocalDate, Long> newRates = new LinkedHashMap<>();
        if (request.dailyRates() != null) {
            request.dailyRates().forEach((dateStr, rate) -> newRates.put(LocalDate.parse(dateStr), rate));
        }

        reservation.updateDailyRates(newRates);
        reservationService.receiveReservations(List.of(reservation));

        return ResponseEntity.ok(ApiResponse.ok("일자별 1박 요금 스케줄이 성공적으로 갱신되었습니다.", null));
    }

    @PatchMapping("/{reservationId}/operational-tags")
    public ResponseEntity<ApiResponse<Void>> updateOperationalTags(
            @PathVariable String reservationId,
            @RequestBody TagOverrideApiRequest request) {
        try {
            Set<String> pref = request.preferredTags() != null ? request.preferredTags() : Set.of();
            Set<String> avoid = request.avoidTags() != null ? request.avoidTags() : Set.of();

            reservationService.updateOperationalTags(reservationId, pref, avoid);
            return ResponseEntity.ok(ApiResponse.ok("현장 운영 태그가 갱신되었습니다. 원본 예약 메모는 보존됩니다.", null));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Reservation>>> searchReservations(
            @ModelAttribute ReservationSearchCondition condition,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "otaChannel", required = false) String otaChannel) {

        ReservationSearchCondition finalCondition = condition;

        String effectiveTag = (tag != null && !tag.isBlank()) ? tag.trim() : (condition != null ? condition.tag() : null);
        String effectiveOta = (otaChannel != null && !otaChannel.isBlank()) ? otaChannel.trim() : (condition != null ? condition.otaChannel() : null);

        if (condition != null && (effectiveTag != null || effectiveOta != null)) {
            finalCondition = new ReservationSearchCondition(
                    condition.reservationId(),
                    condition.guestName(),
                    condition.checkInDate(),
                    condition.stayingDate(),
                    condition.stayNights(),
                    condition.roomType(),
                    condition.status(),
                    condition.assignedRoomNumber(),
                    effectiveTag,
                    effectiveOta
            );
        }

        List<Reservation> reservations = reservationService.searchReservations(finalCondition);
        return ResponseEntity.ok(ApiResponse.ok(reservations));
    }

    @PostMapping("/batch-assign")
    public ResponseEntity<ApiResponse<BatchAssignmentResult>> batchAssign(
            @Valid @RequestBody BatchAssignApiRequest request) {
        BatchAssignmentResult result = reservationService.runDailyBatchAssignment(request.checkInDate());
        return ResponseEntity.ok(ApiResponse.ok("일괄 배정이 완료되었습니다.", result));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Reservation>> getReservation(
            @PathVariable String reservationId) {
        return reservationService.getReservation(reservationId)
                .map(reservation -> ResponseEntity.ok(ApiResponse.ok(reservation)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.fail("해당 예약을 찾을 수 없습니다: " + reservationId)));
    }

    @PostMapping("/{reservationId}/manual-assign")
    public ResponseEntity<ApiResponse<Void>> manualAssign(
            @PathVariable String reservationId,
            @RequestBody Map<String, String> body) {
        String targetRoomNumber = body.get("targetRoomNumber");
        if (targetRoomNumber == null || targetRoomNumber.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("배정할 객실 번호는 필수입니다."));
        }

        String normalizedRoomNumber = normalizeRoomNumber(targetRoomNumber);

        try {
            reservationService.manualAssignRoom(reservationId, normalizedRoomNumber);
            return ResponseEntity.ok(ApiResponse.ok("객실 배정이 완료되었습니다.", null));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/{reservationId}/assign")
    public ResponseEntity<ApiResponse<Void>> unassignRoom(@PathVariable String reservationId) {
        try {
            reservationService.cancelRoomAssignment(reservationId);
            return ResponseEntity.ok(ApiResponse.ok("객실 배정이 취소되고 미배정 상태로 환원되었습니다.", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
        }
    }

    @PatchMapping("/{reservationId}/operational-override")
    public ResponseEntity<ApiResponse<Void>> updateOperationalOverride(
            @PathVariable String reservationId,
            @RequestBody Map<String, Object> body) {
        try {
            String guestName = (String) body.get("operationalGuestName");
            if (guestName == null || guestName.isBlank()) {
                guestName = (String) body.get("guestName");
            }

            String checkInStr = (String) body.get("operationalCheckInDate");
            LocalDate checkIn = (checkInStr != null && !checkInStr.isBlank()) ? LocalDate.parse(checkInStr) : null;

            Integer nights = null;
            Object nightsObj = body.get("operationalStayNights");
            if (nightsObj != null && !nightsObj.toString().isBlank()) {
                nights = Integer.valueOf(nightsObj.toString());
            }

            String memo = (String) body.get("internalStaffMemo");
            if (memo == null) {
                memo = (String) body.get("rawRequestText");
            }

            reservationService.updateOperationalDetails(reservationId, guestName, checkIn, nights, memo);
            return ResponseEntity.ok(ApiResponse.ok("PMS 현장 운영 정보가 반영되었습니다.", null));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("운영 정보 수정 실패: " + e.getMessage()));
        }
    }

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

    @PostMapping("/{reservationId}/room-change")
    public ResponseEntity<ApiResponse<RoomChangeResult>> roomChange(
            @PathVariable String reservationId,
            @Valid @RequestBody RoomChangeApiRequest request) {
        try {
            LocalDate moveDate = (request.moveDate() != null) ? request.moveDate() : LocalDate.now();
            String reason = (request.reason() != null && !request.reason().isBlank()) ? request.reason() : "프론트 현장 요청";
            String targetRoomNumber = normalizeRoomNumber(request.targetRoomNumber());

            RoomChangeRequest domainRequest = new RoomChangeRequest(
                    reservationId,
                    targetRoomNumber,
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

    @PostMapping("/night-audit")
    public ResponseEntity<ApiResponse<NightAuditResult>> runNightAudit(
            @RequestParam(required = false) LocalDate targetDate) {
        LocalDate effectiveDate = (targetDate != null) ? targetDate : LocalDate.now();
        NightAuditResult result = nightAuditService.runNightAudit(effectiveDate);
        return ResponseEntity.ok(ApiResponse.ok(result.message(), result));
    }

    @PostMapping("/generate-test-data")
    public ResponseEntity<ApiResponse<String>> generateTestData(
            @RequestParam(required = false) LocalDate baseDate) {
        LocalDate target = (baseDate != null) ? baseDate : LocalDate.now();
        testDataGeneratorService.generate50DynamicReservations(target);
        return ResponseEntity.ok(ApiResponse.ok(
                String.format("%s 기준 50건의 고유 실명 및 OTA 예약이 생성되고 배정되었습니다.", target),
                null
        ));
    }

    private String normalizeRoomNumber(String input) {
        if (input == null || input.isBlank()) return "";
        String trimmed = input.trim();
        if (trimmed.length() == 3 && Character.isDigit(trimmed.charAt(0))) {
            return "0" + trimmed;
        }
        return trimmed;
    }
}