-- 캠퍼스톡 삭제 (삭제 표시만, 행은 남긴다)
-- 글: 목록·개수에서 빠지고 상세·답변은 404. 답변: 상세에서 "삭제된 답변입니다" 로 보여 대화 순서를 유지
ALTER TABLE talk_post ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE talk_reply ADD COLUMN deleted_at TIMESTAMP;
COMMENT ON COLUMN talk_post.deleted_at IS '삭제 시각 (KST). NULL 이면 삭제 안 됨';
COMMENT ON COLUMN talk_reply.deleted_at IS '삭제 시각 (KST). NULL 이면 삭제 안 됨';
