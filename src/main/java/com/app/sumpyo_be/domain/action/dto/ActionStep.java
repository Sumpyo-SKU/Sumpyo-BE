package com.app.sumpyo_be.domain.action.dto;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.StepType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 조치 진행 단계. action 의 시각 컬럼(window_opened_at 등)이 기록된 것만, 시간 순으로 만든다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "조치 진행 단계")
public class ActionStep {

  @Schema(description = "단계 종류 (WINDOW_OPEN: 창문 열기, WINDOW_CLOSE: 창문 닫기, AC_ON: 에어컨 켜기)",
      example = "WINDOW_OPEN")
  private StepType type;

  @Schema(description = "단계 이름", example = "창문 열기")
  private String label;

  @Schema(description = "수행 시각 (KST)", example = "2026-10-16T14:05:00")
  private LocalDateTime doneAt;

  public static String label(StepType type) {
    return switch (type) {
      case WINDOW_OPEN -> "창문 열기";
      case WINDOW_CLOSE -> "창문 닫기";
      case AC_ON -> "에어컨 켜기";
    };
  }

  public static List<ActionStep> from(Action a) {
    List<ActionStep> steps = new ArrayList<>(3);
    for (StepType type : StepType.values()) {
      LocalDateTime doneAt = a.stepDoneAt(type);
      if (doneAt != null) {
        steps.add(new ActionStep(type, label(type), doneAt));
      }
    }
    steps.sort(Comparator.comparing(ActionStep::getDoneAt));
    return steps;
  }
}
