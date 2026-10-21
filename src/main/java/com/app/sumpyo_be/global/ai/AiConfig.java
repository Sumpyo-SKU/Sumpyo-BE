package com.app.sumpyo_be.global.ai;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * AI(LLM) 설정. LLM_PROVIDER(gemini|openai|anthropic)·LLM_API_KEY·LLM_MODEL 이 모두 있을 때만 LlmClient 빈을 만든다.
 * 없으면 빈이 없고, 쓰는 쪽(예: 캠퍼스톡 추천 답변)이 규칙 기반 등으로 대체한다. 키 값은 로그에 남기지 않는다.
 */
@Slf4j
@Configuration
public class AiConfig {

  /** LLM HTTP 연결·읽기 제한 */
  public static final Duration TIMEOUT = Duration.ofSeconds(3);

  static final String PROVIDER = "sumpyo.llm.provider";
  static final String API_KEY = "sumpyo.llm.api-key";
  static final String MODEL = "sumpyo.llm.model";

  @Bean
  @Conditional(LlmConfigured.class)
  public LlmClient llmClient(Environment env) {
    LlmProvider provider = LlmProvider.parse(env.getProperty(PROVIDER)).orElseThrow();
    String model = env.getProperty(MODEL).trim();
    log.info("LLM 사용: {} ({})", provider, model);
    return new HttpLlmClient(provider, env.getProperty(API_KEY).trim(), model, TIMEOUT);
  }

  /** 세 값이 모두 있고 provider 가 지원하는 값일 때 */
  static class LlmConfigured implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      Environment env = context.getEnvironment();
      String provider = env.getProperty(PROVIDER, "");
      if (provider.isBlank() || env.getProperty(API_KEY, "").isBlank() || env.getProperty(MODEL, "").isBlank()) {
        return false;
      }
      if (LlmProvider.parse(provider).isEmpty()) {
        log.warn("알 수 없는 LLM_PROVIDER '{}' → LLM 을 쓰지 않습니다 (gemini, openai, anthropic 지원)", provider);
        return false;
      }
      return true;
    }
  }
}
