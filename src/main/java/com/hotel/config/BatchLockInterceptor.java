package com.hotel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.api.dto.ApiResponse;
import com.hotel.service.BatchOperationGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 일괄 배정, 해제가 진행 중이면 예약을 바꾸는 요청(조회가 아닌 메서드)을 423으로 거절한다.
 * 화면에서 버튼을 막는 것만으로는 우회할 수 있어서 서버에서 강제한다.
 */
public class BatchLockInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final BatchOperationGuard guard;
    private final ObjectMapper objectMapper;

    public BatchLockInterceptor(BatchOperationGuard guard, ObjectMapper objectMapper) {
        this.guard = guard;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (READ_ONLY_METHODS.contains(request.getMethod())) {
            return true;
        }
        var active = guard.current();
        if (active.isEmpty()) {
            return true;
        }

        response.setStatus(HttpStatus.LOCKED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(active.get().describe() + " 끝날 때까지 예약은 조회만 할 수 있습니다.")));
        return false;
    }
}
