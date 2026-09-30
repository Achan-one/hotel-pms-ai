package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.api.dto.LoginRequest;
import com.hotel.api.dto.LoginResponse;
import com.hotel.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 직원 로그인 및 JWT 토큰 발급
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        try {
            LoginResponse response = // 프록시 뒤에서 X-Forwarded-For를 그대로 믿으면 공격자가 값을 바꿔 잠금을 피하므로 직접 연결된 주소를 쓴다.
            authService.login(request, http.getRemoteAddr());
            return ResponseEntity.ok(ApiResponse.ok("로그인에 성공하였습니다.", response));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(e.getMessage()));
        }
    }
}