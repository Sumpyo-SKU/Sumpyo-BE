package com.app.sumpyo_be.global.ai;

import java.util.Locale;
import java.util.Optional;

/** LLM_PROVIDER 값. gemini 는 OpenAI 호환 Chat Completions 를 쓴다 (요청·응답 형식이 openai 와 같고 주소만 다름) */
public enum LlmProvider {
  GEMINI("https://generativelanguage.googleapis.com", "/v1beta/openai/chat/completions"),
  OPENAI("https://api.openai.com", "/v1/chat/completions"),
  ANTHROPIC("https://api.anthropic.com", "/v1/messages");

  final String baseUrl;
  final String path;

  LlmProvider(String baseUrl, String path) {
    this.baseUrl = baseUrl;
    this.path = path;
  }

  /** 대소문자·앞뒤 공백 무시. 모르는 값이면 empty */
  public static Optional<LlmProvider> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
