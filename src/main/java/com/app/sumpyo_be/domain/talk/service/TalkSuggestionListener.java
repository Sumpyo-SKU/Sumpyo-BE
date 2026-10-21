package com.app.sumpyo_be.domain.talk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 글이 커밋된 뒤 다른 스레드에서 추천 답변을 만든다 → POST /api/talks 는 LLM 을 기다리지 않는다 */
@Component
@RequiredArgsConstructor
public class TalkSuggestionListener {

  private final TalkSuggestionService suggestionService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreated(TalkPostCreatedEvent event) {
    suggestionService.generateAndSave(event.postId());
  }
}
