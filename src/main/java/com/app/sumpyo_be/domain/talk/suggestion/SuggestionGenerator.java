package com.app.sumpyo_be.domain.talk.suggestion;

import java.util.List;

/** 캠퍼스톡 추천 답변 (최대 3개). LLM 이 없거나 늦을 때 쓰는 규칙 기반 구현이 있다 (LLM 은 LlmSuggestionGenerator) */
public interface SuggestionGenerator {

  int MAX_SUGGESTIONS = 3;

  List<String> generate(SuggestionContext context);
}
