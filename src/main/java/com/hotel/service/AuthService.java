package com.hotel.service;

import com.hotel.api.dto.LoginRequest;
import com.hotel.api.dto.LoginResponse;
import com.hotel.security.JwtTokenProvider;
import com.hotel.domain.StaffAccount;
import com.hotel.repository.StaffRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public LoginResponse login(LoginRequest request) {
        StaffAccount account = staffRepository.findByStaffId(request.staffId())
                .orElseThrow(() -> new BadCredentialsException("등록되지 않은 사용자 ID이거나 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new BadCredentialsException("등록되지 않은 사용자 ID이거나 비밀번호가 일치하지 않습니다.");
        }

        String token = tokenProvider.generateToken(account.staffId(), account.role());
        return LoginResponse.of(token, account.staffId(), account.name(), account.role());
    }
}