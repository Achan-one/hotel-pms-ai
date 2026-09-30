package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.service.BatchOperationGuard;
import com.hotel.service.HotelOperationService;
import com.hotel.service.TestDataGeneratorService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 개발용 테스트 데이터 생성 API. 운영에는 등록하지 않는다.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/reservations")
public class TestDataController {

    private final TestDataGeneratorService testDataGeneratorService;
    private final HotelOperationService hotelOperationService;
    private final BatchOperationGuard batchGuard;

    public TestDataController(TestDataGeneratorService testDataGeneratorService,
                              HotelOperationService hotelOperationService,
                              BatchOperationGuard batchGuard) {
        this.testDataGeneratorService = testDataGeneratorService;
        this.hotelOperationService = hotelOperationService;
        this.batchGuard = batchGuard;
    }

    @PostMapping("/generate-test-data")
    public ResponseEntity<ApiResponse<String>> generateTestData(
            @RequestParam(required = false) LocalDate baseDate,
            Authentication authentication) {
        LocalDate target = (baseDate != null) ? baseDate : hotelOperationService.getCurrentBusinessDate();
        // 이 작업도 안에서 AI 태그 분석과 일괄 배정을 세 번 돌린다. 일괄 배정과 똑같이 끝날 때까지 다른 예약 변경을 막는다.
        batchGuard.runExclusive("TEST_DATA_GENERATION", "테스트 데이터 생성(AI 태그 분석·배정 포함)",
                authentication.getName(), target,
                () -> testDataGeneratorService.generate50DynamicReservations(target));
        return ResponseEntity.ok(ApiResponse.ok(
                String.format("%s 기준 50건의 예약이 생성되고 배정되었습니다.", target),
                null
        ));
    }
}
