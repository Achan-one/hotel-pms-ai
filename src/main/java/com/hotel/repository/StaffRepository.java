package com.hotel.repository;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StaffRepository {
    Optional<StaffAccount> findByStaffId(String staffId);
    void save(StaffAccount account);

    /**
     * 이 접속 IP에서 이 계정으로 하는 로그인이 잠겨 있는지. 다른 IP의 로그인에는 영향이 없다.
     */
    boolean isLoginLocked(String staffId, String clientIp, LocalDateTime now);

    /**
     * 로그인 실패를 (계정, 접속 IP) 단위로 기록한다. 누적 실패가 maxAttempts에 이르면 lockUntil까지 잠그고 카운트를 0으로 되돌린다.
     * 존재하는 계정에 대해서만 호출해야 한다.
     */
    void recordLoginFailure(String staffId, String clientIp, int maxAttempts, LocalDateTime lockUntil);

    void recordLoginSuccess(String staffId, String clientIp);

    /**
     * 계정을 활성/비활성으로 바꾸고 그 계정의 모든 IP의 실패 기록과 잠금을 지운다.
     * @return 해당 계정이 있으면 true
     */
    boolean updateEnabled(String staffId, boolean enabled);

    /**
     * 활성 상태인 해당 역할 계정 행을 잠근 채로 개수를 센다. 호출한 트랜잭션이 끝날 때까지 잠금이 유지된다.
     */
    long lockAndCountEnabledByRole(StaffRole role);
}