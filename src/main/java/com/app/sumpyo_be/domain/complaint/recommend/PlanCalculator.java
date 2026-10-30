package com.app.sumpyo_be.domain.complaint.recommend;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 추천의 숫자는 LLM 이 아니라 서버가 계산한다 (617호 실측으로 잡은 설정값).
 * 창문 환기: C(t) = 420 + (C0 − 420) × e^(−k·t), k = 1.8회/시간 (창문 2곳 기준, 창문 수에 비례).
 * 필요 분 = ln((C0 − 420) / (800 − 420)) / k × 60 → 5분 단위 올림, 10~60분 (1,250ppm·2곳 → 30분, 757ppm).
 * 60분으로 목표에 못 미치면 60분 뒤 값을 그대로 쓴다 (예상 효과를 부풀리지 않는다).
 */
public final class PlanCalculator {

  private PlanCalculator() {}

  /** 창문 2곳일 때 시간당 환기 횟수 */
  public static final double K_OPEN_TWO_WINDOWS = 1.8;
  public static final int OUTDOOR_CO2 = 420;
  public static final int TARGET_CO2 = 800;
  public static final int MIN_MINUTES = 10;
  public static final int MAX_MINUTES = 60;
  public static final int MINUTE_STEP = 5;
  /** 현재 CO2 를 모를 때 창문 환기 시간 (결과 측정 시각에도 같은 값) */
  public static final int DEFAULT_MINUTES = 15;

  /** LLM 이 넣은 숫자(분·시간·ppm)와 바로 뒤의 "동안·까지·정도…" */
  private static final Pattern LLM_NUMBERS = Pattern.compile(
      "\\s*약?\\s*\\d[\\d,]*\\s*(분|시간|ppm|PPM)(\\s*(동안|까지|정도|가량|이하|이상|쯤))?");
  private static final Pattern WINDOW_COUNT = Pattern.compile("창문\\s*\\d+\\s*(곳|개)");
  /** "창문을 ", "창문 " → 여기에 "창문 N곳을 M분 " 을 넣는다 */
  private static final Pattern WINDOW_OBJECT = Pattern.compile("창문(을|를)?\\s*");

  static double k(int windows) {
    return K_OPEN_TWO_WINDOWS * windows / 2.0;
  }

  /** 목표(800)까지 필요한 분. 5분 단위 올림, 10~60분. CO2 를 모르면 15분 */
  public static int windowMinutes(Integer co2, int windows) {
    if (co2 == null) {
      return DEFAULT_MINUTES;
    }
    if (co2 <= TARGET_CO2) {
      return MIN_MINUTES;
    }
    double minutes = Math.log((double) (co2 - OUTDOOR_CO2) / (TARGET_CO2 - OUTDOOR_CO2)) / k(windows) * 60;
    int rounded = (int) Math.ceil(minutes / MINUTE_STEP) * MINUTE_STEP;
    return Math.clamp(rounded, MIN_MINUTES, MAX_MINUTES);
  }

  /** minutes 동안 창문을 열었을 때 CO2. 지금 CO2 를 모르면 null */
  public static Integer co2After(Integer co2, int windows, int minutes) {
    if (co2 == null) {
      return null;
    }
    return (int) Math.round(OUTDOOR_CO2 + (co2 - OUTDOOR_CO2) * Math.exp(-k(windows) * minutes / 60.0));
  }

  /** 라벨·창문 분을 채운 최종 단계 */
  public static List<PlanStep> complete(List<PlanStep> raw, Integer co2) {
    List<PlanStep> steps = new ArrayList<>(raw.size());
    for (PlanStep s : raw) {
      steps.add(switch (s.type()) {
        case WINDOW_OPEN -> new PlanStep(StepType.WINDOW_OPEN, "창문 " + s.windows() + "곳 열기", s.windows(), null,
            windowMinutes(co2, s.windows()));
        case WINDOW_CLOSE -> new PlanStep(StepType.WINDOW_CLOSE, "창문 닫기", null, null, null);
        case AC_ON -> new PlanStep(StepType.AC_ON, "에어컨 켜기", null, s.targetTemp(), null);
      });
    }
    return steps;
  }

  public static Optional<PlanStep> windowOpen(List<PlanStep> steps) {
    return steps.stream().filter(s -> s.type() == StepType.WINDOW_OPEN).findFirst();
  }

  /** 창문을 열 때만 CO2 가 내려간다. 창문 단계가 없거나 지금 CO2 를 모르면 null */
  public static Integer expectedCo2After(List<PlanStep> completed, Integer co2) {
    return windowOpen(completed).map(w -> co2After(co2, w.windows(), w.minutes())).orElse(null);
  }

  /** LLM 이 넣은 분·ppm 숫자를 지운다 */
  public static String stripNumbers(String text) {
    if (text == null) {
      return null;
    }
    return LLM_NUMBERS.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ").strip();
  }

  /**
   * 서버 숫자를 넣은 요약. 창문 단계가 있으면 "창문 N곳을 M분 열고 …" 로 만든다.
   * 요약이 비었거나 "창문" 이 없으면 단계로 문장을 새로 만든다.
   */
  public static String summary(String text, List<PlanStep> completed) {
    String cleaned = stripNumbers(text);
    if (cleaned == null || cleaned.isBlank()) {
      cleaned = defaultSummary(completed);
    }
    Optional<PlanStep> window = windowOpen(completed);
    if (window.isEmpty()) {
      return cleaned;
    }
    cleaned = WINDOW_COUNT.matcher(cleaned).replaceAll("창문");
    Matcher m = WINDOW_OBJECT.matcher(cleaned);
    if (!m.find()) {
      cleaned = defaultSummary(completed);
      m = WINDOW_OBJECT.matcher(cleaned);
      m.find();
    }
    String phrase = "창문 " + window.get().windows() + "곳을 " + window.get().minutes() + "분 ";
    return cleaned.substring(0, m.start()) + phrase + cleaned.substring(m.end());
  }

  /** 숫자 없는 기본 문장 (summary 가 이 문장의 "창문" 자리에 숫자를 넣는다) */
  static String defaultSummary(List<PlanStep> steps) {
    boolean open = steps.stream().anyMatch(s -> s.type() == StepType.WINDOW_OPEN);
    boolean close = steps.stream().anyMatch(s -> s.type() == StepType.WINDOW_CLOSE);
    boolean ac = steps.stream().anyMatch(s -> s.type() == StepType.AC_ON);
    if (open && ac) {
      return "창문을 열고 에어컨을 틀어주세요";
    }
    if (open) {
      return "창문을 열어 환기해주세요";
    }
    if (close && ac) {
      return "창문을 닫고 에어컨을 틀어주세요";
    }
    if (close) {
      return "창문을 닫아주세요";
    }
    return ac ? "에어컨을 켜주세요" : "시설팀에 고장 신고해주세요";
  }
}
