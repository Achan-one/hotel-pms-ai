package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.StaffRole;
import com.hotel.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
            @NotBlank(message = "초기 비밀번호는 필수입니다.")
            @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
            String password,
            @NotBlank(message = "직원 이름은 필수입니다.") String name,
            StaffRole role
    ) {}

    public record EnabledRequest(@NotNull(message = "enabled는 필수입니다.") Boolean enabled) {}

    /**
     * 관리자 전용 신규 직원 계정 생성
     * POST /api/admin/staff
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')") // 오직 총지배인만 접근 가능
    public ResponseEntity<ApiResponse<Void>> createStaff(@Valid @RequestBody CreateStaffRequest request) {
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

    /**
     * 직원 계정 활성/비활성 전환 (퇴사 처리, 잠금 해제)
     * PATCH /api/admin/staff/{staffId}/enabled
     */
    @PatchMapping("/{staffId}/enabled")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setEnabled(
            @PathVariable String staffId,
            @Valid @RequestBody EnabledRequest request,
            Authentication authentication) {
        authService.setStaffEnabled(authentication.getName(), staffId, request.enabled());
        return ResponseEntity.ok(ApiResponse.ok(
                request.enabled() ? "계정이 활성화되었습니다." : "계정이 비활성화되었습니다.", null));
    }
}
