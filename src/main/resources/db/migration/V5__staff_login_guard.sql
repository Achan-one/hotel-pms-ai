-- 퇴사자 계정 비활성화와 로그인 무차별 대입 방어에 쓰는 컬럼.
ALTER TABLE staff_accounts
    ADD COLUMN enabled         BOOLEAN  NOT NULL DEFAULT TRUE,
    ADD COLUMN failed_attempts INT      NOT NULL DEFAULT 0,
    ADD COLUMN locked_until    DATETIME NULL;
