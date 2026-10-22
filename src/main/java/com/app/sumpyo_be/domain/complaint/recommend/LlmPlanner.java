package com.app.sumpyo_be.domain.complaint.recommend;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.global.ai.LlmClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 이 조치(단계 종류·창문 수·희망 온도)와 문장을 고른다. 숫자(분·ppm)는 서버가 계산한다 (PlanCalculator).
 * 10초 안에 검증을 통과한 JSON 이 오지 않으면 empty → 규칙 기반으로 대체 (무료 티어 응답이 가끔 5초를 넘긴다).
 * 실패 로그에는 이유만 남긴다 (프롬프트·키·응답 원문은 남기지 않음).
 */
@Slf4j
@Component
public class LlmPlanner {

  static final Duration TIMEOUT = Duration.ofSeconds(10);
  static final int MAX_STEPS = 3;
  static final int MAX_TEXT = 100;
  static final int MIN_WINDOWS = 1;
  static final int MAX_WINDOWS = 4;
  static final int MIN_TEMP = 18;
  static final int MAX_TEMP = 28;
  static final int DEFAULT_WINDOWS = 2;

  /** LLM 설정이 없으면 null */
  private final LlmClient client;
  private final JsonMapper jsonMapper;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  Duration timeout = TIMEOUT;   // 테스트에서 줄인다

  public LlmPlanner(ObjectProvider<LlmClient> client, JsonMapper jsonMapper) {
    this.client = client.getIfAvailable();
    this.jsonMapper = jsonMapper;
  }

  public Optional<RawPlan> plan(PlanContext ctx) {
    if (client == null) {
      return Optional.empty();
    }
    CompletableFuture<String> call = CompletableFuture.supplyAsync(() -> client.complete(prompt(ctx)), executor);
    try {
      Optional<RawPlan> parsed = parse(call.get(timeout.toMillis(), TimeUnit.MILLISECONDS), ctx.comfortableTemp());
      if (parsed.isEmpty()) {
        log.warn("민원 추천 LLM 응답이 형식에 맞지 않아 규칙 기반으로 대체합니다.");
      }
      return parsed;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      call.cancel(true);
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      log.warn("민원 추천 LLM 실패({}), 규칙 기반으로 대체합니다.", cause.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  static String prompt(PlanContext ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append("너는 대학 강의실 환기를 맡은 근로생에게 학생 민원을 처리할 방법을 추천해. 아래 JSON 하나로만 답해. 다른 말은 쓰지 마.\n")
        .append("{\"steps\":[{\"type\":\"WINDOW_OPEN\",\"windows\":2},{\"type\":\"AC_ON\",\"targetTemp\":23}],")
        .append("\"summary\":\"창문을 열고 에어컨을 틀어주세요\",\"reason\":\"…\"}\n")
        .append("규칙:\n")
        .append("- type 은 WINDOW_OPEN, WINDOW_CLOSE, AC_ON 만. steps 는 0~3개, 같은 type 은 한 번만\n")
        .append("- WINDOW_OPEN 은 windows(열 창문 수 1~4), AC_ON 은 targetTemp(희망 온도 18~28)\n")
        .append("- summary 는 근로생이 할 일 한 문장, reason 은 이유 한 문장. 각 100자 이내, 존댓말\n")
        .append("- 분·ppm 같은 숫자는 쓰지 마 (시간과 예상 CO2 는 서버가 넣는다)\n")
        .append("- 답답함·냄새는 창문 환기가 기본. 에어컨은 공기를 바꾸지 못하므로 실내가 적정 온도보다 더울 때만 추가\n")
        .append("- 비 예보가 있거나 미세먼지가 나쁨이면 창문 대신 다른 방법\n")
        .append("- 민원 분류가 설비 고장일 때만 steps 없이 summary 를 \"시설팀에 고장 신고해주세요\" 로. ")
        .append("다른 분류는 지금 수치가 정상이어도 학생이 불편을 느낀 것이니 근로생이 할 수 있는 조치를 골라\n");
    if (ctx.isRetry()) {
      sb.append("- 이전 추천과 다른 방법을 골라\n");
    }
    sb.append("\n상황:\n");
    sb.append("민원: ").append(ctx.categories().stream().map(ComplaintCategory::getLabel)
        .collect(Collectors.joining(", ")));
    if (ctx.content() != null) {
      sb.append(" / 내용: ").append(ctx.content());
    }
    sb.append('\n');
    sb.append("실내: CO2 ").append(orUnknown(ctx.co2(), "ppm")).append(", 온도 ").append(orUnknown(ctx.temp(), "°C"))
        .append(", 습도 ").append(orUnknown(ctx.humidity(), "%")).append('\n');
    if (ctx.reportedCo2() != null || ctx.reportedTemp() != null || ctx.reportedHumidity() != null) {
      sb.append("민원 접수 때: CO2 ").append(orUnknown(ctx.reportedCo2(), "ppm")).append(", 온도 ")
          .append(orUnknown(ctx.reportedTemp(), "°C")).append(", 습도 ").append(orUnknown(ctx.reportedHumidity(), "%"))
          .append('\n');
    }
    String forecast = forecastLine(ctx);
    if (forecast != null) {
      sb.append(forecast).append('\n');
    }
    sb.append("적정: CO2 ").append(ctx.thresholds().co2()).append("ppm 미만, 온도 ").append(ctx.thresholds().tempMin())
        .append("~").append(ctx.thresholds().tempMax()).append("°C, 습도 ").append(ctx.thresholds().humidityMin())
        .append("~").append(ctx.thresholds().humidityMax()).append("%\n");
    sb.append("바깥: 기온 ").append(orUnknown(ctx.outdoorTemp(), "°C")).append(", 습도 ")
        .append(orUnknown(ctx.outdoorHumidity(), "%")).append(", 미세먼지 ").append(orUnknown(ctx.pm10(), "㎍/㎥"))
        .append(ctx.pm10Bad() ? " (나쁨)" : "").append('\n');
    sb.append("1시간 안 비 예보: ").append(ctx.rainSoon() ? "있음" : "없음").append('\n');
    sb.append("지금 열린 창문: ").append(ctx.windowOpen() ? "있음" : "없음").append('\n');
    sb.append("수업: ").append(ctx.currentClass() != null ? "지금 " + ctx.currentClass()
        : ctx.nextClass() != null ? "다음 " + ctx.nextClass() : "없음").append('\n');
    if (ctx.isRetry()) {
      sb.append("이전 추천: ").append(ctx.previous().summary()).append(" (단계: ")
          .append(ctx.previous().steps().stream().map(s -> s.type().name()).collect(Collectors.joining(", ")))
          .append(")\n");
    }
    return sb.toString();
  }

  /** "그대로 두면 30분 뒤 1,180ppm, 수업 종료 시 1,280ppm 예상" (창문 닫힌 상태 예측). 예측이 없으면 null */
  static String forecastLine(PlanContext ctx) {
    List<String> parts = new ArrayList<>();
    if (ctx.co2In30() != null) {
      parts.add("30분 뒤 " + String.format(Locale.KOREA, "%,d", ctx.co2In30()) + "ppm");
    }
    if (ctx.co2AtClassEnd() != null) {
      parts.add("수업 종료 시 " + String.format(Locale.KOREA, "%,d", ctx.co2AtClassEnd()) + "ppm");
    }
    return parts.isEmpty() ? null : "그대로 두면 " + String.join(", ", parts) + " 예상";
  }

  private static String orUnknown(Object value, String unit) {
    return value == null ? "모름" : value + unit;
  }

  /**
   * ```json 코드 블록이 붙어 와도 { ... } 만 읽는다.
   * 검증: type 화이트리스트·중복 없음·열기/닫기 동시 금지, windows 1~4, targetTemp 18~28, steps 0~3개,
   * summary·reason 1~100자. windows·targetTemp 가 없으면 2곳·적정 온도 중간값
   */
  Optional<RawPlan> parse(String text, int defaultTemp) {
    if (text == null) {
      return Optional.empty();
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return Optional.empty();
    }
    JsonNode root;
    try {
      root = jsonMapper.readTree(text.substring(start, end + 1));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    String summary = text(root.get("summary"));
    String reason = text(root.get("reason"));
    JsonNode stepsNode = root.path("steps");
    if (summary == null || reason == null || (!stepsNode.isMissingNode() && !stepsNode.isArray())
        || stepsNode.size() > MAX_STEPS) {
      return Optional.empty();
    }
    List<PlanStep> steps = new ArrayList<>();
    Set<StepType> seen = EnumSet.noneOf(StepType.class);
    for (JsonNode s : stepsNode) {
      StepType type;
      try {
        type = StepType.valueOf(s.path("type").asString(""));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
      if (!seen.add(type)) {
        return Optional.empty();
      }
      Integer windows = null;
      Integer targetTemp = null;
      if (type == StepType.WINDOW_OPEN) {
        windows = intInRange(s.get("windows"), MIN_WINDOWS, MAX_WINDOWS, DEFAULT_WINDOWS);
        if (windows == null) {
          return Optional.empty();
        }
      } else if (type == StepType.AC_ON) {
        targetTemp = intInRange(s.get("targetTemp"), MIN_TEMP, MAX_TEMP, defaultTemp);
        if (targetTemp == null) {
          return Optional.empty();
        }
      }
      steps.add(new PlanStep(type, null, windows, targetTemp, null));
    }
    if (seen.contains(StepType.WINDOW_OPEN) && seen.contains(StepType.WINDOW_CLOSE)) {
      return Optional.empty();
    }
    return Optional.of(new RawPlan(List.copyOf(steps), summary, reason));
  }

  /** 1~100자 문자열, 아니면 null */
  private static String text(JsonNode node) {
    if (node == null || !node.isString()) {
      return null;
    }
    String s = node.asString().strip();
    return s.isEmpty() || s.length() > MAX_TEXT ? null : s;
  }

  /** 없으면 기본값, 정수가 아니거나 범위 밖이면 null(검증 실패) */
  private static Integer intInRange(JsonNode node, int min, int max, int defaultValue) {
    if (node == null || node.isNull()) {
      return defaultValue;
    }
    if (!node.isIntegralNumber()) {
      return null;
    }
    int v = node.asInt();
    return v < min || v > max ? null : v;
  }
}
