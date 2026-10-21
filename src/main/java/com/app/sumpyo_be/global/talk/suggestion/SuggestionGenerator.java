package com.app.sumpyo_be.global.talk.suggestion;

import java.util.List;

/** 캠퍼스톡 추천 답변 (최대 3개). 구현: 규칙 기반(기본) / LLM(설정이 있을 때, 실패하면 규칙 기반) */
public interface SuggestionGenerator {

  int MAX_SUGGESTIONS = 3;

  List<String> generate(SuggestionContext context);
}
