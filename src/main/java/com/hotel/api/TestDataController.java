package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.service.HotelOperationService;
import com.hotel.service.TestDataGeneratorService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
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

    public TestDataController(TestDataGeneratorService testDataGeneratorService,
                              HotelOperationService hotelOperationService) {
        this.testDataGeneratorService = testDataGeneratorService;
        this.hotelOperationService = hotelOperationService;
    }

    @PostMapping("/generate-test-data")
    public ResponseEntity<ApiResponse<String>> generateTestData(
            @RequestParam(required = false) LocalDate baseDate) {
        LocalDate target = (baseDate != null) ? baseDate : hotelOperationService.getCurrentBusinessDate();
        testDataGeneratorService.generate50DynamicReservations(target);
        return ResponseEntity.ok(ApiResponse.ok(
                String.format("%s 기준 50건의 예약이 생성되고 배정되었습니다.", target),
                null
        ));
    }
}
