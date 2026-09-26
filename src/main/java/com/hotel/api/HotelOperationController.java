package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.Reservation;
import com.hotel.service.HotelOperationService;
import com.hotel.service.NightAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class HotelOperationController {

    private final HotelOperationService hotelOperationService;
    private final NightAuditService nightAuditService;

    public HotelOperationController(HotelOperationService hotelOperationService,
                                    NightAuditService nightAuditService) {
        this.hotelOperationService = hotelOperationService;
        this.nightAuditService = nightAuditService;
    }

    /**
     * 호텔 시스템의 공식 영업일자 조회
     * GET /api/system/business-date
     */
    @GetMapping("/business-date")
    public ResponseEntity<ApiResponse<Map<String, String>>> getBusinessDate() {
        LocalDate currentDate = hotelOperationService.getCurrentBusinessDate();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("businessDate", currentDate.toString())));
    }

    /**
     * 호텔 시스템 공식 영업일자 수동 보정 (관리자 전용)
     * PUT /api/system/business-date
     */
    @PutMapping("/business-date")
    public ResponseEntity<ApiResponse<Map<String, String>>> setBusinessDate(@RequestBody Map<String, String> body) {
        String newDateStr = body.get("businessDate");
        if (newDateStr == null || newDateStr.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("businessDate는 필수입니다."));
        }
        LocalDate date = LocalDate.parse(newDateStr.trim());
        hotelOperationService.setBusinessDate(date);
        return ResponseEntity.ok(ApiResponse.ok("공식 영업일자가 갱신되었습니다.", Map.of("businessDate", date.toString())));
    }

    /**
     * 🚀 [누락되었던 핵심 메서드] 나이트 오딧 사전 점검: 당일 미체크인 도착 예정 건수 확인
     * GET /api/system/unchecked-arrivals
     */
    @GetMapping("/unchecked-arrivals")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkUncheckedArrivals(
            @RequestParam(required = false) String targetDate) {
        LocalDate date = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate.trim())
                : hotelOperationService.getCurrentBusinessDate();

        List<Reservation> unchecked = nightAuditService.getUncheckedArrivals(date);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "targetDate", date.toString(),
                "uncheckedCount", unchecked.size(),
                "canRunAudit", unchecked.isEmpty()
        )));
    }

    /**
     * 🚀 [누락되었던 핵심 메서드] 미체크인 예약 익일 일괄 이월 및 단축(체크인 +1일, 박수 -1, 0박 보존)
     * POST /api/system/rollover-unchecked-arrivals
     */
    @PostMapping("/rollover-unchecked-arrivals")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rolloverUncheckedArrivals(
            @RequestParam(required = false) String targetDate) {
        LocalDate date = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate.trim())
                : hotelOperationService.getCurrentBusinessDate();

        int processed = nightAuditService.rolloverUncheckedArrivals(date);
        return ResponseEntity.ok(ApiResponse.ok(
                String.format("총 %d건의 미체크인 예약이 익일로 이월(1박 차감/0박 보존)되었습니다.", processed),
                Map.of("processedCount", processed)
        ));
    }
}