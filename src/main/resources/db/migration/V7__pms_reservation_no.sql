-- PMS가 직접 발급하는 예약 번호. AI 같은 외부 서비스에 예약을 가리킬 때 OTA 예약 ID나 투숙객 정보를 쓰지 않고
-- 이 번호만 쓴다. 기존 reservation_id(기본키)는 그대로 둔다.
ALTER TABLE reservations ADD COLUMN pms_reservation_no VARCHAR(30) NULL;

-- 이미 있는 예약에도 번호를 채운다. UUID()는 행마다 새로 계산된다.
UPDATE reservations
SET pms_reservation_no = CONCAT('PMS-', DATE_FORMAT(NOW(), '%y%m%d'), '-', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8)))
WHERE pms_reservation_no IS NULL;

ALTER TABLE reservations MODIFY pms_reservation_no VARCHAR(30) NOT NULL;
ALTER TABLE reservations ADD CONSTRAINT uk_reservations_pms_no UNIQUE (pms_reservation_no);
