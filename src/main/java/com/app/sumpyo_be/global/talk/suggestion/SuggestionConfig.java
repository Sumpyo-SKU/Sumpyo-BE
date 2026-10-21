package com.app.sumpyo_be.global.talk.suggestion;

import java.time.Duration;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 답변 구현 선택. LLM_PROVIDER(gemini|openai|anthropic)·LLM_API_KEY·LLM_MODEL 이 모두 있을 때만 LLM, 아니면 규칙 기반.
 * 키 값은 로그에 남기지 않는다.
 */
@Slf4j
@Configuration
public class SuggestionConfig {

  static final Duration LLM_TIMEOUT = Duration.ofSeconds(3);

  @Bean
  @Primary
  public SuggestionGenerator suggestionGenerator(RuleBasedSuggestionGenerator ruleBased, JsonMapper jsonMapper,
      @Value("${sumpyo.llm.provider:}") String provider,
      @Value("${sumpyo.llm.api-key:}") String apiKey,
      @Value("${sumpyo.llm.model:}") String model) {
    if (provider.isBlank() || apiKey.isBlank() || model.isBlank()) {
      log.info("캠퍼스톡 추천 답변: 규칙 기반 (LLM 설정 없음)");
      return ruleBased;
    }
    HttpLlmClient.Provider p;
    try {
      p = HttpLlmClient.Provider.valueOf(provider.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      log.warn("캠퍼스톡 추천 답변: 알 수 없는 LLM_PROVIDER '{}' → 규칙 기반 (gemini, openai, anthropic 지원)", provider);
      return ruleBased;
    }
    log.info("캠퍼스톡 추천 답변: LLM ({}, {}), 실패 시 규칙 기반", p, model);
    return new LlmSuggestionGenerator(new HttpLlmClient(p, apiKey.trim(), model.trim(), LLM_TIMEOUT),
        ruleBased, jsonMapper, LLM_TIMEOUT);
  }
}
