package com.app.sumpyo_be.global.action.dto;

import com.app.sumpyo_be.global.action.entity.Action;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 조치 진행 단계. action 의 시각 컬럼(window_opened_at 등)이 기록된 것만, 시간 순으로 만든다. */
public record ActionStep(Type type, String label, LocalDateTime doneAt) {

  @Getter
  @RequiredArgsConstructor
  public enum Type {
    WINDOW_OPEN("창문 열기"),
    WINDOW_CLOSE("창문 닫기"),
    AC_ON("냉난방기 켜기");

    private final String label;
  }

  public static List<ActionStep> from(Action a) {
    List<ActionStep> steps = new ArrayList<>(3);
    add(steps, Type.WINDOW_OPEN, a.getWindowOpenedAt());
    add(steps, Type.AC_ON, a.getAcOnAt());
    add(steps, Type.WINDOW_CLOSE, a.getWindowClosedAt());
    steps.sort(Comparator.comparing(ActionStep::doneAt));
    return steps;
  }

  private static void add(List<ActionStep> steps, Type type, LocalDateTime doneAt) {
    if (doneAt != null) {
      steps.add(new ActionStep(type, type.getLabel(), doneAt));
    }
  }
}
