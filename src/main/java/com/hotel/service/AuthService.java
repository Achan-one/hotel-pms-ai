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

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final long LOCK_MINUTES = 15;

    // 계정 존재 여부나 잠금 여부가 메시지로 드러나지 않도록 모든 실패에 같은 문구를 쓴다.
    private static final String LOGIN_FAILED_MESSAGE =
            "ID 또는 비밀번호가 올바르지 않거나 계정을 사용할 수 없습니다. 계속 실패하면 관리자에게 문의하세요.";

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    // 없는 ID로 시도해도 BCrypt 비교를 똑같이 수행해 응답 시간으로 계정 존재 여부를 알 수 없게 한다.
    private final String dummyPasswordHash;

    public AuthService(StaffRepository staffRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.staffRepository = Objects.requireNonNull(staffRepository, "staffRepository는 필수입니다.");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder는 필수입니다.");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider는 필수입니다.");
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * 직원 로그인 및 JWT 토큰 발급.
     * 실패 횟수는 예외가 던져져도 남아야 하므로 이 메서드는 트랜잭션으로 감싸지 않는다.
     * 저장소 호출이 각자 트랜잭션을 연다.
     */
    public LoginResponse login(LoginRequest request) {
        String staffId = normalize(request.staffId());
        LocalDateTime now = LocalDateTime.now();

        StaffAccount account = staffRepository.findByStaffId(staffId).orElse(null);
        String hashToCheck = (account != null) ? account.passwordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (account == null || !account.enabled() || account.isLocked(now)) {
            throw new BadCredentialsException(LOGIN_FAILED_MESSAGE);
        }
        if (!passwordMatches) {
            staffRepository.recordLoginFailure(
                    account.staffId(), MAX_FAILED_ATTEMPTS, now.plusMinutes(LOCK_MINUTES));
            throw new BadCredentialsException(LOGIN_FAILED_MESSAGE);
        }

        if (account.failedAttempts() > 0 || account.lockedUntil() != null) {
            staffRepository.recordLoginSuccess(account.staffId());
        }

        String token = tokenProvider.generateToken(account.staffId(), account.role());
        return LoginResponse.of(token, account.staffId(), account.name(), account.role());
    }

    /**
     * 계정 활성/비활성 전환. 활성화하면 실패 기록과 잠금도 함께 풀린다.
     * 본인 계정과 마지막 활성 관리자는 비활성화할 수 없다.
     */
    @Transactional
    public void setStaffEnabled(String actorStaffId, String targetStaffId, boolean enabled) {
        String targetId = normalize(targetStaffId);
        StaffAccount target = staffRepository.findByStaffId(targetId)
                .orElseThrow(() -> new NoSuchElementException("등록되지 않은 직원입니다: " + targetStaffId));

        if (!enabled) {
            if (targetId.equals(normalize(actorStaffId))) {
                throw new IllegalArgumentException("본인 계정은 비활성화할 수 없습니다.");
            }
            if (target.enabled() && target.role() == StaffRole.ROLE_ADMIN
                    && staffRepository.countEnabledByRole(StaffRole.ROLE_ADMIN) <= 1) {
                throw new IllegalArgumentException("마지막 활성 관리자 계정은 비활성화할 수 없습니다.");
            }
        }
        staffRepository.updateEnabled(targetId, enabled);
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

        String normalizedStaffId = normalize(staffId);

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

    private static String normalize(String staffId) {
        return staffId == null ? "" : staffId.trim().toLowerCase();
    }
}
