-- 강의실 환기 상태: 직접 [환기 하기](MANUAL), 환기 시작 시각, 자동 완료 여부, 효과 여부
ALTER TABLE action DROP CONSTRAINT ck_action_source;
ALTER TABLE action ADD CONSTRAINT ck_action_source CHECK (source IN ('PREDICTION', 'COMPLAINT', 'MANUAL'));
COMMENT ON COLUMN action.source IS '발생 원인 (PREDICTION: 예측, COMPLAINT: 민원, MANUAL: 추천 없이 직접 환기 하기)';

ALTER TABLE action ADD COLUMN started_at TIMESTAMP;
ALTER TABLE action ADD COLUMN auto_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE action ADD COLUMN effective BOOLEAN;
COMMENT ON COLUMN action.started_at IS '환기 시작 시각 (IN_PROGRESS 가 된 시각). 시작 전이면 NULL';
COMMENT ON COLUMN action.auto_completed IS '서버가 자동 완료했는지 (CO2 800 이하로 떨어짐 또는 시작 후 60분). 사용자가 완료하면 FALSE';
COMMENT ON COLUMN action.effective IS '환기 효과 (actual_co2_after < co2_before). 둘 중 하나라도 없으면 NULL';

-- 이미 진행 중이거나 끝난 조치의 시작 시각: 창문 연 시각, 없으면 생성 시각
UPDATE action SET started_at = COALESCE(window_opened_at, created_at)
WHERE status IN ('IN_PROGRESS', 'DONE');
