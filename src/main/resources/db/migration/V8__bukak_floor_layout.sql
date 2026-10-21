-- 북악관 5·6층 평면도·강의실 정리
-- 0) room 호수 UNIQUE 를 (건물, 호수) → (건물, 층, 호수) 로 (층마다 화장실1·화장실2)
-- 1) 강의실이 아닌 방: 6층 604·605·606·607·612, 5층 504·505·506·507·512 → 기타(ETC), 센서 없음(NONE).
--    평면도 칸(map_side·map_order)은 유지해 회색 NO_SENSOR 로 보인다.
-- 2) 5층 배치 (6층과 같은 구조, 17칸). 없는 방은 INSERT, 있는 방은 UPDATE
--    TOP    1~8 : 521, 520, 519, 화장실1, 화장실2, 518, 517, 516
--    BOTTOM 1~9 : 504, 505, 506, 507, 508, 512, 513, 514, 515
-- 3) 6층 기타 방의 시뮬레이션 데이터 삭제. FK 순서: notification(action_id) → action(prediction_id) → prediction → sensor_reading
--    민원·시간표·캠퍼스톡 글은 방을 그대로 참조하므로 지우지 않는다.

-- ---------- 0. 호수 중복 기준: 건물 → 건물+층 ----------
-- 화장실1·화장실2 처럼 층마다 같은 이름이 있다. 강의실 호수(617 등)는 층이 들어 있어 건물 안에서 겹치지 않는다.
-- building_id FK 인덱스는 새 제약(선두 컬럼)이 겸한다.
ALTER TABLE room DROP CONSTRAINT uk_room_building_number;
ALTER TABLE room ADD CONSTRAINT uk_room_building_floor_number UNIQUE (building_id, floor, number);

-- ---------- 1·2. 5층 배치 (code 가 같으면 배치·종류·센서를 덮어쓴다) ----------
INSERT INTO room (building_id, code, number, floor, type, map_side, map_order, sensor_level)
SELECT b.id, v.code, v.number, 5, v.type, v.map_side, v.map_order, v.sensor_level
FROM building b
CROSS JOIN (VALUES
    ('bukak-521',    '521',   'LECTURE',  'TOP',    1, 'SIM'),
    ('bukak-520',    '520',   'LECTURE',  'TOP',    2, 'SIM'),
    ('bukak-519',    '519',   'LECTURE',  'TOP',    3, 'SIM'),
    ('bukak-5F-WC1', '화장실1', 'RESTROOM', 'TOP',    4, 'NONE'),
    ('bukak-5F-WC2', '화장실2', 'RESTROOM', 'TOP',    5, 'NONE'),
    ('bukak-518',    '518',   'LECTURE',  'TOP',    6, 'SIM'),
    ('bukak-517',    '517',   'LECTURE',  'TOP',    7, 'SIM'),
    ('bukak-516',    '516',   'LECTURE',  'TOP',    8, 'SIM'),
    ('bukak-504',    '504',   'ETC',      'BOTTOM', 1, 'NONE'),
    ('bukak-505',    '505',   'ETC',      'BOTTOM', 2, 'NONE'),
    ('bukak-506',    '506',   'ETC',      'BOTTOM', 3, 'NONE'),
    ('bukak-507',    '507',   'ETC',      'BOTTOM', 4, 'NONE'),
    ('bukak-508',    '508',   'LECTURE',  'BOTTOM', 5, 'SIM'),
    ('bukak-512',    '512',   'ETC',      'BOTTOM', 6, 'NONE'),
    ('bukak-513',    '513',   'LECTURE',  'BOTTOM', 7, 'SIM'),
    ('bukak-514',    '514',   'LECTURE',  'BOTTOM', 8, 'SIM'),
    ('bukak-515',    '515',   'LECTURE',  'BOTTOM', 9, 'SIM')
) AS v (code, number, type, map_side, map_order, sensor_level)
WHERE b.name = '북악관'
ON CONFLICT (code) DO UPDATE
SET type = EXCLUDED.type, map_side = EXCLUDED.map_side, map_order = EXCLUDED.map_order,
    sensor_level = EXCLUDED.sensor_level;

-- ---------- 3. 6층 기타 방 데이터 삭제 후 ETC·NONE ----------
CREATE TEMP TABLE v8_not_lecture ON COMMIT DROP AS
SELECT id FROM room WHERE code IN ('bukak-604', 'bukak-605', 'bukak-606', 'bukak-607', 'bukak-612');

-- 이 방들의 조치 + (혹시 다른 방 조치가) 이 방들의 예측을 가리키는 조치
CREATE TEMP TABLE v8_action ON COMMIT DROP AS
SELECT a.id FROM action a
WHERE a.room_id IN (SELECT id FROM v8_not_lecture)
   OR a.prediction_id IN (SELECT p.id FROM prediction p WHERE p.room_id IN (SELECT id FROM v8_not_lecture));

DELETE FROM notification
WHERE room_id IN (SELECT id FROM v8_not_lecture) OR action_id IN (SELECT id FROM v8_action);
DELETE FROM action WHERE id IN (SELECT id FROM v8_action);
DELETE FROM prediction WHERE room_id IN (SELECT id FROM v8_not_lecture);
DELETE FROM sensor_reading WHERE room_id IN (SELECT id FROM v8_not_lecture);

UPDATE room SET type = 'ETC', sensor_level = 'NONE' WHERE id IN (SELECT id FROM v8_not_lecture);
