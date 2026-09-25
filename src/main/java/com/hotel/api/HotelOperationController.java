package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.service.HotelOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class HotelOperationController {

    private final HotelOperationService hotelOperationService;

    public HotelOperationController(HotelOperationService hotelOperationService) {
        this.hotelOperationService = hotelOperationService;
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
}