-- V5__seed_buildings.sql : 온보딩 건물 선택용 건물 12개 + 화면 표시 순서

ALTER TABLE building ADD COLUMN display_order INT;
COMMENT ON COLUMN building.display_order IS '건물 선택 화면 표시 순서 (1부터)';

UPDATE building SET display_order = 4 WHERE name = '북악관';

-- TODO: 실제 층수 확인 후 max_floor 수정 (혜인관 5층만 확인됨, 나머지는 임시로 7)
INSERT INTO building (name, max_floor, display_order) VALUES
  ('대일관',  7, 1),
  ('문예관',  7, 2),
  ('본관',    7, 3),
  ('상승관',  7, 5),
  ('유담관',  7, 6),
  ('은주1관', 7, 7),
  ('은주2관', 7, 8),
  ('청운관',  7, 9),
  ('한림관',  7, 10),
  ('혜인관',  5, 11),
  ('수인관',  7, 12);

ALTER TABLE building ALTER COLUMN display_order SET NOT NULL;
