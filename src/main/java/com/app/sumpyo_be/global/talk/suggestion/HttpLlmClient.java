package com.app.sumpyo_be.global.talk.suggestion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LLM_PROVIDER 별 HTTP 호출.
 * - anthropic: Messages API
 * - openai: Chat Completions
 * - gemini: Gemini 의 OpenAI 호환 Chat Completions (요청·응답 형식이 openai 와 같고 주소만 다름)
 * API 키는 헤더에만 넣고 로그·예외 메시지에 남기지 않는다.
 */
class HttpLlmClient implements LlmClient {

  enum Provider {
    ANTHROPIC("https://api.anthropic.com", "/v1/messages"),
    OPENAI("https://api.openai.com", "/v1/chat/completions"),
    GEMINI("https://generativelanguage.googleapis.com", "/v1beta/openai/chat/completions");

    final String baseUrl;
    final String path;

    Provider(String baseUrl, String path) {
      this.baseUrl = baseUrl;
      this.path = path;
    }
  }

  static final int MAX_TOKENS = 300;

  private final Provider provider;
  private final String model;
  private final RestClient restClient;

  HttpLlmClient(Provider provider, String apiKey, String model, Duration timeout) {
    this(provider, apiKey, model, RestClient.builder().requestFactory(requestFactory(timeout)));
  }

  /** 테스트에서 MockRestServiceServer 를 붙이려고 builder 를 받는다 */
  HttpLlmClient(Provider provider, String apiKey, String model, RestClient.Builder builder) {
    this.provider = provider;
    this.model = model;
    builder.baseUrl(provider.baseUrl);
    if (provider == Provider.ANTHROPIC) {
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
    if (provider == Provider.ANTHROPIC) {
      List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
      return (String) content.getFirst().get("text");
    }
    // openai · gemini(OpenAI 호환)
    List<Map<String, Object>> choices = (List<Map<String, Object>>) res.get("choices");
    return (String) ((Map<String, Object>) choices.getFirst().get("message")).get("content");
  }
}
