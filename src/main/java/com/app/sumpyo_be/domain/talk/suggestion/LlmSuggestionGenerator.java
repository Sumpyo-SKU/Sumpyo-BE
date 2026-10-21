package com.app.sumpyo_be.domain.talk.suggestion;

import com.app.sumpyo_be.global.ai.LlmClient;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 추천 답변. "JSON 문자열 배열" 이 오지 않거나 호출이 실패하면 empty (규칙 기반 대체·대기 시간은 TalkSuggestionService).
 * 실패 로그에는 예외 종류만 남긴다 (프롬프트·키·응답 원문은 남기지 않음).
 */
@Slf4j
public class LlmSuggestionGenerator {

  static final int MAX_LENGTH = 300;   // 답변 최대 길이와 같다
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final LlmClient client;
  private final JsonMapper jsonMapper;

  public LlmSuggestionGenerator(LlmClient client, JsonMapper jsonMapper) {
    this.client = client;
    this.jsonMapper = jsonMapper;
  }

  /** 응답이 올 때까지 기다린다 (HTTP 타임아웃은 AiConfig.TIMEOUT) */
  public Optional<List<String>> generate(SuggestionContext ctx) {
    try {
      Optional<List<String>> parsed = parse(client.complete(prompt(ctx)));
      if (parsed.isEmpty()) {
        log.warn("LLM 추천 답변 형식이 올바르지 않습니다.");
      }
      return parsed;
    } catch (RuntimeException e) {
      log.warn("LLM 추천 답변 실패({})", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * 본문 + 시간표·센서값. 답변 3개는 의도가 겹치지 않게(바로 가기 / 상황 알려주기 / 지금은 어려움·되묻기).
   * 예시 문장을 그대로 넣으면 그 문장을 베끼므로 유형만 알려준다. 답변은 JSON 배열로만
   */
  static String prompt(SuggestionContext ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append("대학 강의실 환기를 맡은 근로생끼리 쓰는 메신저야. 아래 메시지를 받은 근로생이 버튼 한 번으로 바로 보낼 ")
        .append("답변 3개를 만들어줘.\n")
        .append("- 3개는 서로 다른 의도로: ① 바로 하겠다·가겠다 ② 아래 정보로 지금 상황 알려주기 ")
        .append("③ 지금은 어렵다(다른 곳에 있음, 몇 분 뒤 가능 등) 또는 필요한 걸 되묻기\n")
        .append("- 메시지 내용에 맞춰 구체적으로 (강의실 호수, 수업 끝나는 시각, CO2 등을 활용). 같은 표현 반복 금지\n")
        .append("- 아래에 없는 강의실 호수·수업·CO2 수치는 절대 지어내지 마. 정보가 없으면 ②는 메시지에 맞는 내 상황으로\n")
        .append("- 동료끼리 쓰는 짧고 자연스러운 존댓말, 각 30자 이내\n")
        .append("- JSON 문자열 배열로만 답해. 다른 말은 쓰지 마.\n\n");
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
      if (!trimmed.isEmpty() && trimmed.length() <= MAX_LENGTH && result.size() < SuggestionGenerator.MAX_SUGGESTIONS) {
        result.add(trimmed);
      }
    }
    return result.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(result));
  }
}
