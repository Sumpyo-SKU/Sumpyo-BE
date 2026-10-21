package com.app.sumpyo_be.global.talk.suggestion;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 추천 답변. 전체 3초 안에 "JSON 문자열 배열" 이 오지 않으면 규칙 기반으로 대체한다.
 * 실패 로그에는 예외 종류만 남긴다 (프롬프트·키·응답 원문은 남기지 않음).
 */
@Slf4j
public class LlmSuggestionGenerator implements SuggestionGenerator {

  static final int MAX_LENGTH = 300;   // 답변 최대 길이와 같다
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final LlmClient client;
  private final SuggestionGenerator fallback;
  private final JsonMapper jsonMapper;
  private final Duration timeout;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public LlmSuggestionGenerator(LlmClient client, SuggestionGenerator fallback, JsonMapper jsonMapper,
      Duration timeout) {
    this.client = client;
    this.fallback = fallback;
    this.jsonMapper = jsonMapper;
    this.timeout = timeout;
  }

  @Override
  public List<String> generate(SuggestionContext ctx) {
    try {
      String text = CompletableFuture.supplyAsync(() -> client.complete(prompt(ctx)), executor)
          .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      Optional<List<String>> parsed = parse(text);
      if (parsed.isPresent()) {
        return parsed.get();
      }
      log.warn("LLM 추천 답변 형식이 올바르지 않아 규칙 기반으로 대체합니다.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("LLM 추천 답변 중단, 규칙 기반으로 대체합니다.");
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      log.warn("LLM 추천 답변 실패({}), 규칙 기반으로 대체합니다.", cause.getClass().getSimpleName());
    }
    return fallback.generate(ctx);
  }

  /** 본문 + 시간표·센서값. 답변은 JSON 배열로만 */
  static String prompt(SuggestionContext ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append("대학 강의실 환기를 맡은 근로생끼리 쓰는 메신저야. 아래 메시지를 받은 근로생이 바로 보낼 ")
        .append("한국어 짧은 답변 3개를 JSON 문자열 배열로만 답해. 다른 말은 쓰지 마.\n\n");
    sb.append("메시지: ").append(ctx.content()).append('\n');
    sb.append("보낸 곳: ").append(ctx.building()).append(' ').append(ctx.floor()).append("층\n");
    if (ctx.hasRoom()) {
      sb.append("대상 강의실: ").append(ctx.roomNumber()).append("호\n");
      sb.append("지금 수업: ").append(ctx.inClass()
          ? ctx.courseName() + " (" + ctx.classEndTime().format(HH_MM) + "까지)"
          : "없음").append('\n');
      if (ctx.co2() != null) {
        sb.append("지금 CO2: ").append(ctx.co2()).append("ppm\n");
      }
    }
    return sb.toString();
  }

  /** ```json 코드 블록이 붙어 와도 [ ... ] 만 꺼내 읽는다. 문자열이 아닌 원소가 있으면 실패 */
  Optional<List<String>> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start < 0 || end <= start) {
      return Optional.empty();
    }
    List<?> raw;
    try {
      raw = jsonMapper.readValue(text.substring(start, end + 1), List.class);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    Set<String> result = new LinkedHashSet<>();
    for (Object o : raw) {
      if (!(o instanceof String s)) {
        return Optional.empty();
      }
      String trimmed = s.strip();
      if (!trimmed.isEmpty() && trimmed.length() <= MAX_LENGTH && result.size() < MAX_SUGGESTIONS) {
        result.add(trimmed);
      }
    }
    return result.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(result));
  }
}
