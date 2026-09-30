-- 같은 예약을 동시에 수정할 때 나중 저장이 앞선 변경을 덮어쓰지 않도록 낙관적 락 버전을 둔다.
ALTER TABLE reservations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
