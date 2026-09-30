package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.service.BatchOperationGuard;
import com.hotel.service.ReservationLockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservations/{reservationId}/lock")
public class ReservationLockController {

    private final ReservationLockService lockService;
    private final BatchOperationGuard batchGuard;

    public ReservationLockController(ReservationLockService lockService, BatchOperationGuard batchGuard) {
        this.lockService = lockService;
        this.batchGuard = batchGuard;
    }

    public record LockRequest(String staffName) {}

    /**
     * 편집 락 점유 시도
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> acquireLock(
            @PathVariable String reservationId,
            @RequestBody(required = false) LockRequest request,
            Authentication auth) {

        var batch = batchGuard.current();
        if (batch.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok(batch.get().describe(), batchLockedBody(batch.get())));
        }

        String staffId = auth != null ? auth.getName() : "anonymous";
        String staffName = (request != null && request.staffName() != null && !request.staffName().isBlank())
                ? request.staffName() : staffId;

        boolean acquired = lockService.acquireLock(reservationId, staffId, staffName);
        Optional<ReservationLockService.LockInfo> lockOpt = lockService.getLockInfo(reservationId);

        if (acquired) {
            return ResponseEntity.ok(ApiResponse.ok("편집 락 점유 성공", Map.of(
                    "isLockedByOther", false,
                    "lockedByStaffId", staffId,
                    "lockedByStaffName", staffName
            )));
        } else {
            ReservationLockService.LockInfo holder = lockOpt.orElse(new ReservationLockService.LockInfo("unknown", "다른 직원", null));
            return ResponseEntity.ok(ApiResponse.ok("다른 직원이 편집 중입니다.", Map.of(
                    "isLockedByOther", true,
                    "lockedByStaffId", holder.staffId(),
                    "lockedByStaffName", holder.staffName()
            )));
        }
    }

    /**
     * 락 점유 상태 확인
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLockStatus(
            @PathVariable String reservationId,
            Authentication auth) {

        var batch = batchGuard.current();
        if (batch.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok(batchLockedBody(batch.get())));
        }

        String currentStaffId = auth != null ? auth.getName() : "";
        Optional<ReservationLockService.LockInfo> lockOpt = lockService.getLockInfo(reservationId);

        if (lockOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("isLockedByOther", false)));
        }

        ReservationLockService.LockInfo lock = lockOpt.get();
        boolean isOther = !lock.staffId().equalsIgnoreCase(currentStaffId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "isLockedByOther", isOther,
                "lockedByStaffId", lock.staffId(),
                "lockedByStaffName", lock.staffName()
        )));
    }

    /**
     * 락 점유 해제
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> releaseLock(
            @PathVariable String reservationId,
            Authentication auth) {

        String staffId = auth != null ? auth.getName() : "";
        lockService.releaseLock(reservationId, staffId);
        return ResponseEntity.ok(ApiResponse.ok("편집 락이 해제되었습니다.", null));
    }

    // 일괄 작업 중에는 모든 예약이 그 작업에 잠긴 것으로 응답해 화면이 읽기 전용으로 열리게 한다.
    private Map<String, Object> batchLockedBody(BatchOperationGuard.Active active) {
        return Map.of(
                "isLockedByOther", true,
                "lockedByStaffId", active.staffId(),
                "lockedByStaffName", active.label() + " 진행 중",
                "lockedByBatch", true
        );
    }
}
