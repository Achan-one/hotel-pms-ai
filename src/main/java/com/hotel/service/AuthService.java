package com.hotel.service;

import com.hotel.api.dto.LoginRequest;
import com.hotel.api.dto.LoginResponse;
import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import com.hotel.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(StaffRepository staffRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.staffRepository = Objects.requireNonNull(staffRepository, "staffRepository는 필수입니다.");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder는 필수입니다.");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider는 필수입니다.");
    }

    /**
     * 직원 로그인 및 JWT 토큰 발급
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        StaffAccount account = staffRepository.findByStaffId(request.staffId())
                .orElseThrow(() -> new BadCredentialsException("등록되지 않은 사용자 ID이거나 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new BadCredentialsException("등록되지 않은 사용자 ID이거나 비밀번호가 일치하지 않습니다.");
        }

        String token = tokenProvider.generateToken(account.staffId(), account.role());
        return LoginResponse.of(token, account.staffId(), account.name(), account.role());
    }

    /**
     * 총지배인(관리자)에 의한 신규 직원 계정 등록 (비밀번호 BCrypt 암호화 저장)
     */
    @Transactional
    public void registerStaffByAdmin(String staffId, String rawPassword, String name, StaffRole role) {
        if (staffId == null || staffId.isBlank()) {
            throw new IllegalArgumentException("직원 ID는 필수입니다.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("직원 이름은 필수입니다.");
        }

        String normalizedStaffId = staffId.trim().toLowerCase();

        if (staffRepository.findByStaffId(normalizedStaffId).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 직원 ID입니다: " + normalizedStaffId);
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        StaffAccount newAccount = new StaffAccount(
                normalizedStaffId,
                encodedPassword,
                name.trim(),
                role != null ? role : StaffRole.ROLE_STAFF
        );

        staffRepository.save(newAccount);
    }
}