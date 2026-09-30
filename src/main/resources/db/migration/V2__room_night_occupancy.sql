-- 같은 방의 같은 날짜가 두 번 점유되지 않도록 DB가 직접 막는다.
-- 기간은 [체크인, 체크아웃) 반개구간이라 체크아웃일은 점유로 보지 않는다.
CREATE TABLE room_night_occupancy (
    room_number VARCHAR(10) NOT NULL,
    stay_date   DATE        NOT NULL,
    PRIMARY KEY (room_number, stay_date),
    CONSTRAINT fk_night_room FOREIGN KEY (room_number)
        REFERENCES rooms (room_number) ON DELETE CASCADE
);

-- 이미 들어 있는 스케줄을 박 단위로 펼쳐서 옮긴다.
-- V1에는 겹침을 막는 장치가 없었으니, 겹친 데이터가 있으면 여기서 기본키 위반으로 멈춘다.
INSERT INTO room_night_occupancy (room_number, stay_date)
WITH RECURSIVE nights AS (
    SELECT room_number, check_in_date AS d, check_out_date AS out_d
    FROM room_schedules
    UNION ALL
    SELECT room_number, DATE_ADD(d, INTERVAL 1 DAY), out_d
    FROM nights
    WHERE DATE_ADD(d, INTERVAL 1 DAY) < out_d
)
SELECT room_number, d FROM nights;
