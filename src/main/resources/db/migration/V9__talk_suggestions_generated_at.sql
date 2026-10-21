-- 캠퍼스톡 추천 답변 생성 시각. 30분이 지나면 상세 조회 때 규칙 기반으로 다시 만든다 (수업·CO2 가 바뀌었을 수 있어서)
-- 기존 행은 NULL → 다음 조회 때 오래된 것으로 보고 다시 만든다
ALTER TABLE talk_post ADD COLUMN suggestions_generated_at TIMESTAMP;
COMMENT ON COLUMN talk_post.suggestions_generated_at IS '추천 답변 생성 시각 (KST)';
