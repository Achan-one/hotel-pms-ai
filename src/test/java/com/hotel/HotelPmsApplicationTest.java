package com.hotel;

import com.hotel.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class HotelPmsApplicationTest {

    @Autowired
    private ReservationService reservationService;

    @Test
    @DisplayName("[Spring Boot Context] 스프링 컨텍스트가 로드되고 도메인 빈이 주입되어야 한다")
    void contextLoads() {
        assertNotNull(reservationService, "ReservationService 빈이 스프링 컨텍스트에 정상 등록되어야 합니다.");
    }
}