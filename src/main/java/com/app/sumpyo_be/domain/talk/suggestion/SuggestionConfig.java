package com.app.sumpyo_be.domain.talk.suggestion;

import com.app.sumpyo_be.global.ai.AiConfig;
import com.app.sumpyo_be.global.ai.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/** 추천 답변 구현 선택. LLM(global.ai.LlmClient)이 설정돼 있으면 LLM + 실패 시 규칙 기반, 아니면 규칙 기반 */
@Slf4j
@Configuration
public class SuggestionConfig {

  @Bean
  @Primary
  public SuggestionGenerator suggestionGenerator(RuleBasedSuggestionGenerator ruleBased,
      ObjectProvider<LlmClient> llmClient, JsonMapper jsonMapper) {
    LlmClient client = llmClient.getIfAvailable();
    if (client == null) {
      log.info("캠퍼스톡 추천 답변: 규칙 기반 (LLM 설정 없음)");
      return ruleBased;
    }
    log.info("캠퍼스톡 추천 답변: LLM, 실패 시 규칙 기반");
    return new LlmSuggestionGenerator(client, ruleBased, jsonMapper, AiConfig.TIMEOUT);
  }
}
