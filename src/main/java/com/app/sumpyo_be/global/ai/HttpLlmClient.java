package com.app.sumpyo_be.global.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LlmProvider 별 HTTP 호출 (anthropic: Messages API, openai·gemini: Chat Completions).
 * API 키는 헤더에만 넣고 로그·예외 메시지에 남기지 않는다.
 */
public class HttpLlmClient implements LlmClient {

  static final int MAX_TOKENS = 300;

  private final LlmProvider provider;
  private final String model;
  private final RestClient restClient;

  public HttpLlmClient(LlmProvider provider, String apiKey, String model, Duration timeout) {
    this(provider, apiKey, model, RestClient.builder().requestFactory(requestFactory(timeout)));
  }

  /** 테스트에서 MockRestServiceServer 를 붙이려고 builder 를 받는다 */
  HttpLlmClient(LlmProvider provider, String apiKey, String model, RestClient.Builder builder) {
    this.provider = provider;
    this.model = model;
    builder.baseUrl(provider.baseUrl);
    if (provider == LlmProvider.ANTHROPIC) {
      builder.defaultHeader("x-api-key", apiKey).defaultHeader("anthropic-version", "2023-06-01");
    } else {
      builder.defaultHeader("Authorization", "Bearer " + apiKey);
    }
    this.restClient = builder.build();
  }

  private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    return factory;
  }

  @Override
  @SuppressWarnings("unchecked")
  public String complete(String prompt) {
    Map<String, Object> body = Map.of("model", model, "max_tokens", MAX_TOKENS,
        "messages", List.of(Map.of("role", "user", "content", prompt)));
    Map<String, Object> res = restClient.post().uri(provider.path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve().body(Map.class);
    if (provider == LlmProvider.ANTHROPIC) {
      List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
      return (String) content.getFirst().get("text");
    }
    // openai · gemini(OpenAI 호환)
    List<Map<String, Object>> choices = (List<Map<String, Object>>) res.get("choices");
    return (String) ((Map<String, Object>) choices.getFirst().get("message")).get("content");
  }
}
