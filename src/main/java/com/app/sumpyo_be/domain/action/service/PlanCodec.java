package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** action.plan JSON ↔ PlanStep 목록 */
@Component
@RequiredArgsConstructor
public class PlanCodec {

  private final JsonMapper jsonMapper;

  /** plan 이 null 이면 빈 목록 */
  public List<PlanStep> read(String plan) {
    if (plan == null || plan.isBlank()) {
      return List.of();
    }
    return jsonMapper.readValue(plan, new TypeReference<List<PlanStep>>() {});
  }

  public String write(List<PlanStep> steps) {
    return jsonMapper.writeValueAsString(steps);
  }
}
