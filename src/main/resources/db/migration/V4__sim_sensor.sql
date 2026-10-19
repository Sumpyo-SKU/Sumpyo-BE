-- V4__sim_sensor.sql : 시뮬레이터 센서(SIM) 추가
-- 실제 센서는 617호(FULL) 하나뿐이라, 나머지 강의실은 서버 시뮬레이터가 1분마다 측정값을 만든다.
-- 화장실 등 강의실이 아닌 곳은 NONE 그대로.

ALTER TABLE room DROP CONSTRAINT ck_room_sensor_level;
ALTER TABLE room ADD CONSTRAINT ck_room_sensor_level CHECK (sensor_level IN ('FULL', 'TH', 'NONE', 'SIM'));

UPDATE room SET sensor_level = 'SIM' WHERE type = 'LECTURE' AND sensor_level = 'NONE';

COMMENT ON COLUMN room.sensor_level IS '센서 수준 (FULL: CO2+온습도, TH: 온습도만, NONE: 없음, SIM: 시뮬레이터)';
