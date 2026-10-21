package com.app.sumpyo_be.domain.talk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.talk.entity.TalkPost;
import com.app.sumpyo_be.domain.talk.repository.TalkPostRepository;
import com.app.sumpyo_be.domain.talk.suggestion.RuleBasedSuggestionGenerator;
import com.app.sumpyo_be.global.ai.LlmClient;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

/** 비동기 생성(LLM → 규칙 기반)과 상세 조회 규칙(30분). 강의실이 없는 글이라 재료는 건물·층·본문뿐 */
class TalkSuggestionServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 0);
  private static final long POST_ID = 7L;
  private static final List<String> RULES = List.of("지금 북악관 1층에 있어요");

  private final LlmClient client = mock(LlmClient.class);
  private final TalkPostRepository postRepository = mock(TalkPostRepository.class);
  private final TalkPost post = mock(TalkPost.class);

  @BeforeEach
  void setUp() {
    Building building = mock(Building.class);
    when(building.getName()).thenReturn("북악관");
    when(post.getId()).thenReturn(POST_ID);
    when(post.getBuilding()).thenReturn(building);
    when(post.getFloor()).thenReturn(1);
    when(post.getContent()).thenReturn("1층에 계신 분 있나요?");
    when(postRepository.findDetailById(POST_ID)).thenReturn(Optional.of(post));
  }

  @SuppressWarnings("unchecked")
  private TalkSuggestionService service(LlmClient llmClient) {
    ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(llmClient);
    return new TalkSuggestionService(new RuleBasedSuggestionGenerator(), provider,
        JsonMapper.builder().build(), postRepository, mock(RoomRepository.class), mock(ClassScheduleService.class),
        mock(SensorReadingRepository.class), Clock.fixed(NOW.atZone(KST).toInstant(), KST));
  }

  @Nested
  class GenerateAndSave {

    @Test
    void LLM_답변을_생성_시각과_함께_저장() {
      when(client.complete(anyString())).thenReturn("[\"네 1층이에요\"]");
      service(client).generateAndSave(POST_ID);
      verify(postRepository).saveSuggestions(POST_ID, "[\"네 1층이에요\"]", NOW);
    }

    @Test
    void LLM_이_실패하면_규칙_기반을_저장() {
      when(client.complete(anyString())).thenThrow(new IllegalStateException("429"));
      service(client).generateAndSave(POST_ID);
      verify(postRepository).saveSuggestions(POST_ID, "[\"지금 북악관 1층에 있어요\"]", NOW);
    }

    @Test
    void LLM_설정이_없으면_규칙_기반을_저장() {
      service(null).generateAndSave(POST_ID);
      verify(postRepository).saveSuggestions(POST_ID, "[\"지금 북악관 1층에 있어요\"]", NOW);
    }
  }

  @Nested
  class ForDetail {

    @Test
    void 이십구분_전_저장값은_그대로_LLM_도_안_부른다() {
      when(post.getSuggestions()).thenReturn("[\"저장된 추천\"]");
      when(post.getSuggestionsGeneratedAt()).thenReturn(NOW.minusMinutes(29));

      assertThat(service(client).forDetail(post)).containsExactly("저장된 추천");
      verify(client, never()).complete(anyString());
      verify(postRepository, never()).saveSuggestions(anyLong(), anyString(), any());
    }

    @Test
    void 아직_없으면_규칙_기반_저장_안_함() {
      assertThat(service(client).forDetail(post)).isEqualTo(RULES);
      verify(client, never()).complete(anyString());
      verify(postRepository, never()).saveSuggestions(anyLong(), anyString(), any());
    }

    @Test
    void 삼십분이_지났거나_생성_시각이_없으면_규칙_기반으로_다시_저장() {
      when(post.getSuggestions()).thenReturn("[\"오래된 추천\"]");
      for (LocalDateTime generatedAt : new LocalDateTime[] {NOW.minusMinutes(30), null}) {
        when(post.getSuggestionsGeneratedAt()).thenReturn(generatedAt);
        assertThat(service(client).forDetail(post)).isEqualTo(RULES);
      }
      verify(client, never()).complete(anyString());
      verify(postRepository, org.mockito.Mockito.times(2))
          .saveSuggestions(POST_ID, "[\"지금 북악관 1층에 있어요\"]", NOW);
    }
  }
}
