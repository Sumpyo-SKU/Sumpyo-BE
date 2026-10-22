-- 민원 접수함: 민원 조치의 추천 체크리스트(plan)·이유·추천 출처, 결과 CO2 를 잴 시각
ALTER TABLE action ADD COLUMN plan TEXT;
ALTER TABLE action ADD COLUMN reason VARCHAR(200);
ALTER TABLE action ADD COLUMN plan_source VARCHAR(10);
ALTER TABLE action ADD COLUMN measure_at TIMESTAMP;
ALTER TABLE action ADD CONSTRAINT ck_action_plan_source CHECK (plan_source IN ('LLM', 'RULE'));

COMMENT ON COLUMN action.plan IS '추천 체크리스트 JSON 배열 [{type, label, windows, targetTemp, minutes}] (type: WINDOW_OPEN·WINDOW_CLOSE·AC_ON)';
COMMENT ON COLUMN action.reason IS '추천 이유 한 줄';
COMMENT ON COLUMN action.plan_source IS '추천 출처 (LLM, RULE: 규칙 기반 대체)';
COMMENT ON COLUMN action.measure_at IS '결과 CO2 를 잴 시각 (창문 연 시각 + 추천 분). 이 시각 측정값이 actual_co2_after';

-- 결과 CO2 측정 스캔: 완료됐고 아직 결과가 없는 조치
CREATE INDEX idx_action_measure ON action (measure_at) WHERE status = 'DONE' AND actual_co2_after IS NULL;

-- 민원 접수함 목록: 최신순 무한 스크롤 (created_at, id) 커서
CREATE INDEX idx_complaint_created_id ON complaint (created_at DESC, id DESC);
