package com.hotel.service;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import com.hotel.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceAdminGuardTest {

    private StaffRepository staffRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        staffRepository = mock(StaffRepository.class);
        authService = new AuthService(staffRepository, new BCryptPasswordEncoder(4),
                new JwtTokenProvider("unit-test-secret-key-0123456789abcdef-xyz", 60_000L));
    }

    @Test
    @DisplayName("[계정 관리] 마지막 활성 관리자는 다른 관리자가 비활성화할 수 없다")
    void lastActiveAdminCannotBeDisabled() {
        when(staffRepository.findByStaffId("only-admin"))
                .thenReturn(Optional.of(new StaffAccount("only-admin", "h", "관리자", StaffRole.ROLE_ADMIN)));
        when(staffRepository.countEnabledByRole(StaffRole.ROLE_ADMIN)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> authService.setStaffEnabled("someone-else", "only-admin", false));
        verify(staffRepository, never()).updateEnabled(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("[계정 관리] 관리자가 둘 이상이면 하나는 비활성화할 수 있다")
    void adminCanBeDisabledWhenAnotherRemains() {
        when(staffRepository.findByStaffId("admin-b"))
                .thenReturn(Optional.of(new StaffAccount("admin-b", "h", "관리자B", StaffRole.ROLE_ADMIN)));
        when(staffRepository.countEnabledByRole(StaffRole.ROLE_ADMIN)).thenReturn(2L);

        authService.setStaffEnabled("admin-a", "admin-b", false);

        verify(staffRepository).updateEnabled("admin-b", false);
    }
}
