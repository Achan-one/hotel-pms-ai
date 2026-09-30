-- 로그인 실패 횟수와 잠금을 (계정, 접속 IP) 단위로 관리한다.
-- 계정 단위로만 잠그면 누구나 남의 ID로 틀린 비밀번호를 보내 그 계정을 계속 잠글 수 있다.
CREATE TABLE login_attempts (
    staff_id        VARCHAR(50) NOT NULL,
    client_ip       VARCHAR(64) NOT NULL,
    failed_attempts INT         NOT NULL DEFAULT 0,
    locked_until    DATETIME    NULL,
    PRIMARY KEY (staff_id, client_ip),
    CONSTRAINT fk_login_attempt_staff FOREIGN KEY (staff_id)
        REFERENCES staff_accounts (staff_id) ON DELETE CASCADE
);

-- V5에서 계정 행에 두었던 컬럼은 더 쓰지 않는다.
ALTER TABLE staff_accounts
    DROP COLUMN failed_attempts,
    DROP COLUMN locked_until;
