-- 같은 영업일의 나이트 오딧을 두 번 실행해 객실료가 중복 청구되지 않도록 마지막으로 마감한 영업일을 기록한다.
ALTER TABLE hotel_operation_status ADD COLUMN last_audited_date DATE NULL;
