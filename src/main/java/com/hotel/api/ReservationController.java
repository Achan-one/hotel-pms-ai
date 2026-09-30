package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.api.dto.BatchAssignApiRequest;
import com.hotel.api.dto.ReservationResponse;
import com.hotel.api.dto.RoomChangeApiRequest;
import com.hotel.domain.Reservation;
import com.hotel.service.BatchAssignmentResult;
import com.hotel.service.BatchOperationGuard;
import com.hotel.service.BatchUnassignResult;
import com.hotel.service.NightAuditService;
import com.hotel.service.ReservationService;
import com.hotel.service.HotelOperationService;
import com.hotel.service.dto.NightAuditResult;
import com.hotel.service.dto.PageResult;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final NightAuditService nightAuditService;
    private final HotelOperationService hotelOperationService;

    private final BatchOperationGuard batchGuard;

    public ReservationController(ReservationService reservationService,
                                 NightAuditService nightAuditService,
                                 HotelOperationService hotelOperationService,
                                 BatchOperationGuard batchGuard) {
        this.reservationService = reservationService;
        this.nightAuditService = nightAuditService;
        this.hotelOperationService = hotelOperationService;
        this.batchGuard = batchGuard;
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

    // 예약별 원장 수납/청구 거래 등록 (청구와 수납 동시 분개 지원)
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
        Set<String> pref = request.preferredTags() != null ? request.preferredTags() : Set.of();
        Set<String> avoid = request.avoidTags() != null ? request.avoidTags() : Set.of();

        reservationService.updateOperationalTags(reservationId, pref, avoid);
        return ResponseEntity.ok(ApiResponse.ok("현장 운영 태그가 갱신되었습니다. 원본 예약 메모는 보존됩니다.", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ReservationResponse>>> searchReservations(
            @ModelAttribute ReservationSearchCondition condition,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "otaChannel", required = false) String otaChannel,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            Authentication authentication) {

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

        PageResult<Reservation> result = reservationService.searchReservations(
                finalCondition, PageResult.normalizePage(page), PageResult.normalizeSize(size));
        boolean includeMemo = canSeeStaffMemo(authentication);
        return ResponseEntity.ok(ApiResponse.ok(result.map(r -> ReservationResponse.from(r, includeMemo))));
    }

    @PostMapping("/batch-assign")
    public ResponseEntity<ApiResponse<BatchAssignmentResult>> batchAssign(
            @Valid @RequestBody BatchAssignApiRequest request,
            Authentication authentication) {
        // 진행 중에는 다른 요청이 예약을 바꾸지 못한다(BatchLockInterceptor). 끝나거나 실패하면 반드시 풀린다.
        BatchAssignmentResult result = batchGuard.runExclusive(
                "BATCH_ASSIGN", "AI 일괄 자동 배정", authentication.getName(), request.checkInDate(),
                () -> reservationService.runDailyBatchAssignment(request.checkInDate()));
        return ResponseEntity.ok(ApiResponse.ok("일괄 배정이 완료되었습니다.", result));
    }

    /**
     * 선택한 체크인 일자의 배정 완료 예약을 일괄로 미배정으로 되돌린다.
     */
    @PostMapping("/batch-unassign")
    public ResponseEntity<ApiResponse<BatchUnassignResult>> batchUnassign(
            @Valid @RequestBody BatchAssignApiRequest request,
            Authentication authentication) {
        BatchUnassignResult result = batchGuard.runExclusive(
                "BATCH_UNASSIGN", "일괄 배정 해제", authentication.getName(), request.checkInDate(),
                () -> reservationService.runDailyBatchUnassign(request.checkInDate()));
        return ResponseEntity.ok(ApiResponse.ok(
                String.format("%s 체크인 예약 %d건의 배정을 해제했습니다.", request.checkInDate(), result.releasedCount()), result));
    }

    /**
     * 일괄 작업 진행 여부. 프론트가 주기적으로 조회해 예약 화면을 읽기 전용으로 전환한다.
     */
    @GetMapping("/batch-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchStatus() {
        var active = batchGuard.current();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("active", active.isPresent());
        active.ifPresent(a -> {
            body.put("operation", a.operation());
            body.put("label", a.label());
            body.put("staffId", a.staffId());
            body.put("targetDate", a.targetDate().toString());
            body.put("startedAt", a.startedAt().toString());
        });
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(
            @PathVariable String reservationId,
            Authentication authentication) {
        boolean includeMemo = canSeeStaffMemo(authentication);
        return reservationService.getReservation(reservationId)
                .map(reservation -> ResponseEntity.ok(ApiResponse.ok(ReservationResponse.from(reservation, includeMemo))))
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

        reservationService.manualAssignRoom(reservationId, normalizedRoomNumber);
        return ResponseEntity.ok(ApiResponse.ok("객실 배정이 완료되었습니다.", null));
    }

    @DeleteMapping("/{reservationId}/assign")
    public ResponseEntity<ApiResponse<Void>> unassignRoom(@PathVariable String reservationId) {
        reservationService.cancelRoomAssignment(reservationId);
        return ResponseEntity.ok(ApiResponse.ok("객실 배정이 취소되고 미배정 상태로 환원되었습니다.", null));
    }

    @PatchMapping("/{reservationId}/operational-override")
    public ResponseEntity<ApiResponse<Void>> updateOperationalOverride(
            @PathVariable String reservationId,
            @RequestBody Map<String, Object> body) {
        String guestName = text(body, "operationalGuestName");
        if (guestName == null || guestName.isBlank()) {
            guestName = text(body, "guestName");
        }

        String checkInStr = text(body, "operationalCheckInDate");
        LocalDate checkIn = (checkInStr != null && !checkInStr.isBlank()) ? LocalDate.parse(checkInStr) : null;

        String nightsStr = text(body, "operationalStayNights");
        Integer nights = (nightsStr != null && !nightsStr.isBlank()) ? Integer.valueOf(nightsStr.trim()) : null;

        String memo = text(body, "internalStaffMemo");
        if (memo == null) {
            memo = text(body, "rawRequestText");
        }

        reservationService.updateOperationalDetails(reservationId, guestName, checkIn, nights, memo);
        return ResponseEntity.ok(ApiResponse.ok("PMS 현장 운영 정보가 반영되었습니다.", null));
    }

    @PostMapping("/{reservationId}/check-in")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @PathVariable String reservationId) {
        reservationService.processCheckIn(reservationId);
        return ResponseEntity.ok(ApiResponse.ok("체크인이 완료되었습니다.", null));
    }

    @PostMapping("/{reservationId}/room-change")
    public ResponseEntity<ApiResponse<RoomChangeResult>> roomChange(
            @PathVariable String reservationId,
            @Valid @RequestBody RoomChangeApiRequest request) {
        LocalDate moveDate = (request.moveDate() != null) ? request.moveDate() : hotelOperationService.getCurrentBusinessDate();
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
    }

    @PostMapping("/{reservationId}/check-out")
    public ResponseEntity<ApiResponse<Void>> checkOut(
            @PathVariable String reservationId,
            @RequestParam(required = false) LocalDate checkOutDate) {
        LocalDate effectiveDate = (checkOutDate != null) ? checkOutDate : hotelOperationService.getCurrentBusinessDate();
        reservationService.processCheckOut(reservationId, effectiveDate);
        return ResponseEntity.ok(ApiResponse.ok("퇴실 처리가 완료되었습니다.", null));
    }

    @PostMapping("/night-audit")
    public ResponseEntity<ApiResponse<NightAuditResult>> runNightAudit(
            @RequestParam(required = false) LocalDate targetDate) {
        LocalDate effectiveDate = (targetDate != null) ? targetDate : hotelOperationService.getCurrentBusinessDate();
        NightAuditResult result = nightAuditService.runNightAudit(effectiveDate);
        return ResponseEntity.ok(ApiResponse.ok(result.message(), result));
    }

    // 내부 인계 메모는 아르바이트(PART_TIME)에게는 보이지 않는다.
    private static boolean canSeeStaffMemo(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
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