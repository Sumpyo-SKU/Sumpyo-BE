package com.app.sumpyo_be.domain.talk.service;

/** 캠퍼스톡 글 저장. 커밋 후 추천 답변을 비동기로 만든다 (TalkSuggestionListener) */
public record TalkPostCreatedEvent(Long postId) {
}
