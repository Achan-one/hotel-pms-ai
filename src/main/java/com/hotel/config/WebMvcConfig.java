package com.hotel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.service.BatchOperationGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final BatchOperationGuard guard;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(BatchOperationGuard guard, ObjectMapper objectMapper) {
        this.guard = guard;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new BatchLockInterceptor(guard, objectMapper))
                // 개발용 시뮬레이션 API(전체 초기화, 대량 적재 등)도 일괄 작업 중에는 막는다. 운영에는 등록되지 않는 경로다.
                .addPathPatterns("/api/reservations/**", "/api/system/rollover-unchecked-arrivals", "/api/simulation/**")
                // 편집 락 조회/해제는 일괄 작업 중에도 동작해야 화면이 읽기 전용으로 전환된다.
                .excludePathPatterns("/api/reservations/*/lock");
    }
}
