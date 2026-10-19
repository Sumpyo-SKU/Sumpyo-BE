-- V3__migrate_legacy_readings.sql : 기존 측정 데이터(sensor_reading_legacy) → 새 sensor_reading 이관
-- legacy 테이블은 운영 DB에만 있다. 없으면(새 DB) 아무 일도 하지 않는다.
--
-- room_id = '517'     : 센서가 실제로는 617호에 있음('517'은 예전에 잘못 붙인 값) → bukak-617 로 이관
-- room_id = '517-sim' : 시뮬레이션 데이터 → 이관하지 않음
-- legacy 테이블은 지우지 않는다. 이관 결과 확인 후 수동으로 DROP 할 것.

DO $$
DECLARE
    target_room_id BIGINT;
    moved_count    BIGINT;
BEGIN
    IF to_regclass('sensor_reading_legacy') IS NULL THEN
        RAISE NOTICE 'sensor_reading_legacy 없음 → 이관 생략';
        RETURN;
    END IF;

    SELECT id INTO STRICT target_room_id FROM room WHERE code = 'bukak-617';

    INSERT INTO sensor_reading (room_id, co2, temp, humidity, measured_at)
    SELECT target_room_id, l.co2, l.temp, l.humidity, l.measured_at
    FROM sensor_reading_legacy l
    WHERE l.room_id = '517'
    ORDER BY l.measured_at, l.id;

    GET DIAGNOSTICS moved_count = ROW_COUNT;
    RAISE NOTICE 'sensor_reading_legacy(room_id=517) → bukak-617 이관: % 건', moved_count;
END $$;
