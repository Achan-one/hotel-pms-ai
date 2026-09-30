package com.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * (계정, 접속 IP)별 로그인 실패 기록. 값 갱신은 원자적 쿼리로만 하므로 읽기 전용 엔티티처럼 쓴다.
 */
@Entity
@Table(name = "login_attempts")
@IdClass(LoginAttemptEntity.Key.class)
public class LoginAttemptEntity {

    @Id
    @Column(name = "staff_id", length = 50)
    private String staffId;

    @Id
    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    protected LoginAttemptEntity() {}

    public String getStaffId() { return staffId; }
    public String getClientIp() { return clientIp; }
    public int getFailedAttempts() { return failedAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public static class Key implements Serializable {
        private String staffId;
        private String clientIp;

        public Key() {}

        public Key(String staffId, String clientIp) {
            this.staffId = staffId;
            this.clientIp = clientIp;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(staffId, k.staffId) && Objects.equals(clientIp, k.clientIp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(staffId, clientIp);
        }
    }
}
