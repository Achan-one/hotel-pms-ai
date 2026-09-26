-- =========================================================================
-- 1. 시스템 기본 물리 태그 카탈로그 시드 적재
-- =========================================================================
MERGE INTO room_tags (code, name, category, strictness, default_weight, is_system_default, description)
    KEY(code) VALUES
    ('HIGH_FLOOR', '고층', 'FLOOR', 'SOFT', 15, true, '10층 이상의 상층부 객실. 고층 전망, 뷰 선호'),
    ('LOW_FLOOR', '저층', 'FLOOR', 'HARD', 15, true, '6층 이하 저층 객실. 보행 편의, 어르신/유아 동반'),
    ('NEAR_ELEVATOR', '엘리베이터 인접', 'LOCATION', 'HARD', 20, true, '엘리베이터와 가까워 이동이 편함'),
    ('AWAY_FROM_ELEVATOR', '엘리베이터 이격', 'LOCATION', 'SOFT', 20, true, '엘리베이터와 멀리 떨어진 복도 안쪽 방. 소음 차단'),
    ('CORNER_ROOM', '코너룸', 'VIEW', 'SOFT', 15, true, '건물 모퉁이 끝방. 2면 창문, 독립적 공간'),
    ('QUIET_ZONE', '조용한 방', 'NOISE', 'HARD', 25, true, '소음 민감 고객, 아기 동반, 수면 방해 최소화'),
    ('ACCESSIBLE', '배리어프리', 'AMENITY', 'HARD', 40, true, '휠체어 이동 및 장애인/노약자 편의 설비 완비');

-- =========================================================================
-- 2. 191실 물리 도면 인벤토리 시드 적재
--    규격:
--    - 3F~13F (일반층 11개 층 x 15실 = 165실, 13호 결번)
--    - 14F~15F (상층부 2개 층 x 13실 = 26실, 03호/07호/13호 결번)
-- =========================================================================

-- [03층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0301', 3, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0302', 3, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0303', 3, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0304', 3, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0305', 3, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0306', 3, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0307', 3, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0308', 3, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0309', 3, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0310', 3, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0311', 3, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0312', 3, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0314', 3, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0315', 3, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0316', 3, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [04층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0401', 4, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0402', 4, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0403', 4, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0404', 4, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0405', 4, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0406', 4, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0407', 4, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0408', 4, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0409', 4, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0410', 4, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0411', 4, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0412', 4, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0414', 4, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0415', 4, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0416', 4, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [05층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0501', 5, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0502', 5, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0503', 5, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0504', 5, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0505', 5, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0506', 5, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0507', 5, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0508', 5, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0509', 5, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0510', 5, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0511', 5, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0512', 5, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0514', 5, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0515', 5, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0516', 5, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [06층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0601', 6, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0602', 6, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0603', 6, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0604', 6, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0605', 6, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0606', 6, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0607', 6, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0608', 6, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0609', 6, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0610', 6, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0611', 6, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0612', 6, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0614', 6, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0615', 6, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0616', 6, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [07층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0701', 7, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0702', 7, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0703', 7, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0704', 7, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0705', 7, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0706', 7, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0707', 7, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0708', 7, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0709', 7, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0710', 7, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0711', 7, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0712', 7, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0714', 7, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0715', 7, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0716', 7, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [08층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0801', 8, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0802', 8, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0803', 8, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0804', 8, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0805', 8, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0806', 8, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0807', 8, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0808', 8, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0809', 8, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0810', 8, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0811', 8, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0812', 8, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0814', 8, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0815', 8, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0816', 8, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [09층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('0901', 9, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0902', 9, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0903', 9, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0904', 9, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0905', 9, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0906', 9, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('0907', 9, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0908', 9, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('0909', 9, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('0910', 9, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0911', 9, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('0912', 9, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0914', 9, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('0915', 9, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('0916', 9, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [10층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('1001', 10, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1002', 10, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1003', 10, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1004', 10, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1005', 10, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1006', 10, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1007', 10, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1008', 10, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1009', 10, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1010', 10, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1011', 10, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1012', 10, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1014', 10, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1015', 10, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1016', 10, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [11층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('1101', 11, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1102', 11, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1103', 11, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1104', 11, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1105', 11, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1106', 11, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1107', 11, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1108', 11, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1109', 11, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1110', 11, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1111', 11, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1112', 11, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1114', 11, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1115', 11, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1116', 11, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [12층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('1201', 12, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1202', 12, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1203', 12, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1204', 12, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1205', 12, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1206', 12, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1207', 12, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1208', 12, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1209', 12, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1210', 12, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1211', 12, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1212', 12, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1214', 12, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1215', 12, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1216', 12, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [13층]
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('1301', 13, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1302', 13, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1303', 13, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1304', 13, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1305', 13, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1306', 13, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1307', 13, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1308', 13, 'MODERATE_DOUBLE', true, false, 'VACANT'),
    ('1309', 13, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1310', 13, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1311', 13, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1312', 13, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1314', 13, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1315', 13, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1316', 13, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [14층 특수층] (03호, 07호, 13호 결번 / 04, 08호 이그제큐티브 더블)
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('1401', 14, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1402', 14, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1404', 14, 'EXECUTIVE_DOUBLE', true, false, 'VACANT'),
    ('1405', 14, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1406', 14, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1408', 14, 'EXECUTIVE_DOUBLE', true, false, 'VACANT'),
    ('1409', 14, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1410', 14, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1411', 14, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1412', 14, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1414', 14, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1415', 14, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1416', 14, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- [15층 특수층] (03호, 07호, 13호 결번 / 04, 08호 이그제큐티브 더블)
MERGE INTO rooms (room_number, floor, room_type, near_elevator, corner_room, status) KEY(room_number) VALUES
    ('1501', 15, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1502', 15, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1504', 15, 'EXECUTIVE_DOUBLE', true, false, 'VACANT'),
    ('1505', 15, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1506', 15, 'SUPERIOR_DOUBLE', true, false, 'VACANT'),
    ('1508', 15, 'EXECUTIVE_DOUBLE', true, false, 'VACANT'),
    ('1509', 15, 'RESIDENTIAL_DOUBLE', false, true, 'VACANT'),
    ('1510', 15, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1511', 15, 'SUPERIOR_DOUBLE', false, false, 'VACANT'),
    ('1512', 15, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1514', 15, 'SUPERIOR_TWIN', false, true, 'VACANT'),
    ('1515', 15, 'MODERATE_DOUBLE', false, false, 'VACANT'),
    ('1516', 15, 'MODERATE_DOUBLE', false, true, 'VACANT');

-- =========================================================================
-- 3. 표준 계정과목(Charge Codes) 카탈로그 시드 적재
-- =========================================================================
MERGE INTO folio_charge_codes (code, name, default_amount, is_system_default)
    KEY(code) VALUES
    ('ROOM_CHARGE', '룸 차지', 0, true),
    ('EXTRA_BED', '엑스트라 베드', 3000, true),
    ('MINIBAR', '미니바', 1000, true),
    ('ROOM_CHANGE', '룸 체인지 추가금', 0, true),
    ('EARLY_CHECKIN', '얼리 체크인', 2000, true),
    ('LATE_CHECKOUT', '레이트 체크아웃', 2000, true);