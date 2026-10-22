-- 민원 추천 때의 "그대로 두면" CO2 예측 (수업 종료 시각, 수업이 없으면 30분 뒤). 화면 "그대로 두면 1,280ppm"
ALTER TABLE action ADD COLUMN forecast_co2 INT;
COMMENT ON COLUMN action.forecast_co2 IS '추천 당시 그대로 두면 예상 CO2 (ppm, predict.py 예측). 예측이 없으면 NULL';
