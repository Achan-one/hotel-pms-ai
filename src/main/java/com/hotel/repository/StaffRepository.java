package com.hotel.repository;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StaffRepository {
    Optional<StaffAccount> findByStaffId(String staffId);
    void save(StaffAccount account);

    /**
     * 로그인 실패를 기록한다. 누적 실패가 maxAttempts에 이르면 lockUntil까지 잠그고 카운트를 0으로 되돌린다.
     */
    void recordLoginFailure(String staffId, int maxAttempts, LocalDateTime lockUntil);

    void recordLoginSuccess(String staffId);

    /**
     * 계정을 활성/비활성으로 바꾼다. 실패 기록과 잠금도 함께 지운다.
     * @return 해당 계정이 있으면 true
     */
    boolean updateEnabled(String staffId, boolean enabled);

    long countEnabledByRole(StaffRole role);
}