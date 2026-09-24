package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.StaffRole;
import com.hotel.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

    private final AuthService authService;

    public AdminStaffController(AuthService authService) {
        this.authService = authService;
    }

    public record CreateStaffRequest(
            @NotBlank(message = "직원 ID는 필수입니다.") String staffId,
            @NotBlank(message = "초기 비밀번호는 필수입니다.") String password,
            @NotBlank(message = "직원 이름은 필수입니다.") String name,
            StaffRole role
    ) {}

    /**
     * 관리자 전용 신규 직원 계정 생성
     * POST /api/admin/staff
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')") // 오직 총지배인만 접근 가능
    public ResponseEntity<ApiResponse<Void>> createStaff(@RequestBody CreateStaffRequest request) {
        try {
            authService.registerStaffByAdmin(
                    request.staffId(),
                    request.password(),
                    request.name(),
                    request.role()
            );
            return ResponseEntity.ok(ApiResponse.ok(
                    String.format("[%s (%s)] 신규 직원이 등록되었습니다.", request.name(), request.staffId()),
                    null
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        }
    }
}