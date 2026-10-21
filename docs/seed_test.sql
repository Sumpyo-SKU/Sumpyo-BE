-- seed_test.sql : 스웨거 테스트용 (로컬 전용, 운영 DB 금지)
-- 앱을 한 번 띄워 Flyway 가 적용된 뒤에 실행 (V8 부터 604~607·612 는 기타 방이라 쓰지 않음). 시각은 모두 "지금" 기준.
-- V4 이후 강의실은 SIM(시뮬레이터)이지만, 608/616/618 은 값이 고정돼야 해서 FULL 로 바꿔 시뮬레이터 대상에서 뺀다.
-- 다른 SIM 방은 시뮬레이터가 계속 값을 넣으므로 alertCount 등을 정확히 맞추려면 SIMULATOR_ENABLED=false 로 앱을 띄울 것.

UPDATE room SET capacity = 42 WHERE code = 'bukak-617';
UPDATE room SET sensor_level = 'FULL' WHERE code IN ('bukak-608', 'bukak-616');

-- 617: 오늘 09~18시 1분 간격 + 1분 전 1250ppm → OVER
INSERT INTO sensor_reading (room_id, co2, temp, humidity, measured_at)
SELECT (SELECT id FROM room WHERE code='bukak-617'), 500 + (extract(minute FROM g)::int * 10), 23, 45, g
FROM generate_series(current_date + time '09:00', current_date + time '17:59', interval '1 minute') g;
INSERT INTO sensor_reading (room_id, co2, temp, humidity, measured_at)
VALUES ((SELECT id FROM room WHERE code='bukak-617'), 1250, 24, 61, localtimestamp - interval '1 minute');

-- 608: 900ppm + 20분 뒤 1100 예측 → PREDICTED (같은 시각의 예전 예측 800은 무시돼야 함)
INSERT INTO sensor_reading (room_id, co2, temp, humidity, measured_at)
VALUES ((SELECT id FROM room WHERE code='bukak-608'), 900, 22, 50, localtimestamp - interval '2 minute');
INSERT INTO prediction (room_id, created_at, target_at, co2, temp, humidity, model_version) VALUES
 ((SELECT id FROM room WHERE code='bukak-608'), localtimestamp - interval '30 minute', date_trunc('minute', localtimestamp) + interval '20 minute', 800, 22.0, 50, 'lgbm-v0'),
 ((SELECT id FROM room WHERE code='bukak-608'), localtimestamp - interval '1 minute',  date_trunc('minute', localtimestamp) + interval '20 minute', 1100, 22.5, 51, 'lgbm-v1');

-- 616: 마지막 측정이 20분 전 → NO_SENSOR(끊김)
INSERT INTO sensor_reading (room_id, co2, temp, humidity, measured_at)
VALUES ((SELECT id FROM room WHERE code='bukak-616'), 700, 22, 50, localtimestamp - interval '20 minute');

-- 617 그래프 예측 (10분 간격 6개)
INSERT INTO prediction (room_id, created_at, target_at, co2, temp, humidity, model_version)
SELECT (SELECT id FROM room WHERE code='bukak-617'), localtimestamp, date_trunc('minute', localtimestamp) + g * interval '10 minute', 1200 - g*50, 24.3, 50, 'lgbm-v1'
FROM generate_series(1, 6) g;

-- 617 오늘 수업: 지금 진행 중 / 휴강 / 다음 (자정 근처에 실행하면 시간이 넘어가니 주의)
INSERT INTO class_schedule (room_id, course_name, day_of_week, start_time, end_time, enrolled, semester) VALUES
 ((SELECT id FROM room WHERE code='bukak-617'), '테스트-현재수업', extract(isodow FROM current_date), localtime - interval '30 minute', localtime + interval '30 minute', 18, '2026-2'),
 ((SELECT id FROM room WHERE code='bukak-617'), '테스트-휴강수업', extract(isodow FROM current_date), localtime + interval '35 minute', localtime + interval '40 minute', NULL, '2026-2'),
 ((SELECT id FROM room WHERE code='bukak-617'), '테스트-다음수업', extract(isodow FROM current_date), localtime + interval '45 minute', localtime + interval '90 minute', NULL, '2026-2');
INSERT INTO class_cancellation (class_schedule_id, cancel_date, memo)
SELECT id, current_date, '테스트 휴강' FROM class_schedule WHERE course_name = '테스트-휴강수업';

-- 618: 테스트용 FULL, 정상(700ppm) + 다음 수업 → nextClass.tip 이 null 이어야 함 (#26)
UPDATE room SET sensor_level = 'FULL' WHERE code = 'bukak-618';
INSERT INTO sensor_reading (room_id, co2, temp, humidity, measured_at)
VALUES ((SELECT id FROM room WHERE code='bukak-618'), 700, 23, 50, localtimestamp - interval '1 minute');
INSERT INTO class_schedule (room_id, course_name, day_of_week, start_time, end_time, enrolled, semester) VALUES
 ((SELECT id FROM room WHERE code='bukak-618'), '테스트-618다음수업', extract(isodow FROM current_date), localtime + interval '20 minute', localtime + interval '60 minute', NULL, '2026-2');

-- 민원 + 조치 2개 (617: 민원 연결, 608: 예측)
INSERT INTO complaint (room_id, categories, content, created_at)
VALUES ((SELECT id FROM room WHERE code='bukak-617'), 'STUFFY,HOT', '답답해요', localtimestamp - interval '5 minute');
INSERT INTO action (room_id, source, complaint_id, recommend, metric, threshold, summary, expected_co2_after, status, created_at) VALUES
 ((SELECT id FROM room WHERE code='bukak-617'), 'COMPLAINT', (SELECT max(id) FROM complaint), 'OPEN', 'CO2', 1000, '민원: 답답해요 · 창문 열기', 800, 'PENDING', localtimestamp - interval '4 minute'),
 ((SELECT id FROM room WHERE code='bukak-608'), 'PREDICTION', NULL, 'OPEN', 'CO2', 1000, '20분 뒤 CO2 1100 예상', 850, 'PENDING', localtimestamp - interval '1 minute');

-- 스웨거에 넣을 ID 확인
SELECT r.id AS room_id, r.code FROM room r WHERE code IN ('bukak-617','bukak-608','bukak-616','bukak-618','bukak-6F-WC1') ORDER BY r.id;
SELECT id AS action_id, room_id, status FROM action ORDER BY id;
