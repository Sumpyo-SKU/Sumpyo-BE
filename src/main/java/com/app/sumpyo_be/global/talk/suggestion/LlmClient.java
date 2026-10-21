package com.app.sumpyo_be.global.talk.suggestion;

/** LLM 호출 (응답 본문 텍스트). 실패하면 예외를 던진다. 테스트에서는 mock */
public interface LlmClient {

  String complete(String prompt);
}
