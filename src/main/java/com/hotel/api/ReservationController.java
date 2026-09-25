package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.api.dto.BatchAssignApiRequest;
import com.hotel.api.dto.RoomChangeApiRequest;
import com.hotel.domain.Reservation;
import com.hotel.service.BatchAssignmentResult;
import com.hotel.service.NightAuditService;
import com.hotel.service.ReservationService;
import com.hotel.service.dto.NightAuditResult;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final NightAuditService nightAuditService;

    // 단일 생성자로 정의하여 final 필드 초기화 누락 방지 및 스프링 자동 주입 보장
    public ReservationController(ReservationService reservationService,
                                 NightAuditService nightAuditService) {
        this.reservationService = reservationService;
        this.nightAuditService = nightAuditService;
    }

    public record TagOverrideApiRequest(
            Set<String> preferredTags,
            Set<String> avoidTags
    ) {}

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

    /**
     * 예약 목록 다조건 검색
     * - @RequestParam(value = "tag", required = false)를 명시적으로 받아
     *   Spring Record 바인딩 누락을 원천 방어하고 condition에 확실하게 주입합니다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Reservation>>> searchReservations(
            @ModelAttribute ReservationSearchCondition condition,
            @RequestParam(value = "tag", required = false) String tag) {

        ReservationSearchCondition finalCondition = condition;

        // 쿼리스트링 ?tag=... 가 넘어왔는데 condition.tag()에 바인딩되지 않은 경우 보정 주입
        if (tag != null && !tag.isBlank() && (condition == null || condition.tag() == null || condition.tag().isBlank())) {
            finalCondition = new ReservationSearchCondition(
                    condition != null ? condition.reservationId() : null,
                    condition != null ? condition.guestName() : null,
                    condition != null ? condition.checkInDate() : null,
                    condition != null ? condition.stayingDate() : null,
                    condition != null ? condition.stayNights() : null,
                    condition != null ? condition.roomType() : null,
                    condition != null ? condition.status() : null,
                    condition != null ? condition.assignedRoomNumber() : null,
                    tag.trim()
            );
        }

        List<Reservation> reservations = reservationService.searchReservations(finalCondition);
        return ResponseEntity.ok(ApiResponse.ok(reservations));
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
     * [PMS 수동 배정] 입실 전 객실 수동 지정 및 재배정 (3자리/4자리 호실 번호 정규화 적용)
     */
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

    /**
     * [PMS 배정 취소] 방 빼기 (스케줄 회수 및 PENDING 상태 환원)
     */
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

    /**
     * [PMS 운영 오버라이드] 원본 계약은 보존하고 현장 운영 상태(이름, 일정, 메모) 수정
     */
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

    /**
     * 체크인 실행
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
     * 룸 체인지 실행 (3자리/4자리 호실 번호 정규화 적용)
     */
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

    /**
     * 체크아웃 실행
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

    /**
     * [나이트 오딧 실행] 자정 마감 정산 및 노쇼 일괄 정리
     * POST /api/reservations/night-audit?targetDate=2026-09-20
     */
    @PostMapping("/night-audit")
    public ResponseEntity<ApiResponse<NightAuditResult>> runNightAudit(
            @RequestParam(required = false) LocalDate targetDate) {
        LocalDate effectiveDate = (targetDate != null) ? targetDate : LocalDate.now();
        NightAuditResult result = nightAuditService.runNightAudit(effectiveDate);
        return ResponseEntity.ok(ApiResponse.ok(result.message(), result));
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