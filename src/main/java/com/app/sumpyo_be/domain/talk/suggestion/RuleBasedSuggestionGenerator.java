package com.app.sumpyo_be.domain.talk.suggestion;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 외부 API 없이 시간표·센서·본문 키워드로 만드는 추천 답변.
 * 순서: 강의실 수업 여부 → 본문 키워드(창문/에어컨) → CO2 → "지금 {건물} {층}층에 있어요"(항상).
 * 최대 3개이고 위치 문구는 항상 들어가므로, 앞의 후보는 2개까지만 쓴다.
 */
@Component
public class RuleBasedSuggestionGenerator implements SuggestionGenerator {

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  @Override
  public List<String> generate(SuggestionContext ctx) {
    List<String> candidates = new ArrayList<>();
    if (ctx.hasRoom()) {
      candidates.add(ctx.inClass()
          ? "%s호 지금 수업 중이에요 (%s, %s까지)".formatted(ctx.roomNumber(), ctx.courseName(),
              ctx.classEndTime().format(HH_MM))
          : "%s호 지금 수업 없어요".formatted(ctx.roomNumber()));
    }
    String content = ctx.content() == null ? "" : ctx.content();
    if (content.contains("창문")) {
      candidates.add("창문 확인 완료 했어요");
    }
    if (content.contains("에어컨")) {
      candidates.add("에어컨 확인 완료 했어요");
    }
    if (ctx.hasRoom() && ctx.co2() != null) {
      candidates.add(String.format(Locale.KOREA, "%s호 지금 CO2 %,dppm이에요", ctx.roomNumber(), ctx.co2()));
    }

    Set<String> result = new LinkedHashSet<>();
    for (String c : candidates) {
      if (result.size() == MAX_SUGGESTIONS - 1) {
        break;
      }
      result.add(c);
    }
    result.add("지금 %s %d층에 있어요".formatted(ctx.building(), ctx.floor()));
    return List.copyOf(result);
  }
}
