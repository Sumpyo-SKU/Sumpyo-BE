package com.app.sumpyo_be.global.ai;

/**
 * LLM 호출 창구 (프롬프트 → 응답 텍스트). 실패하면 예외를 던진다.
 * LLM_PROVIDER·LLM_API_KEY·LLM_MODEL 이 모두 있을 때만 빈으로 등록된다 (AiConfig). 테스트에서는 mock
 */
public interface LlmClient {

  String complete(String prompt);
}
