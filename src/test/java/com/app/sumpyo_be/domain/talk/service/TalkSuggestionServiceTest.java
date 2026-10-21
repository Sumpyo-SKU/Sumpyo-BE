package com.app.sumpyo_be.domain.talk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

/** LLM 대기·백그라운드 저장. 강의실이 없는 글이라 재료는 건물·층·본문뿐 */
class TalkSuggestionServiceTest {

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
    TalkSuggestionService service = new TalkSuggestionService(new RuleBasedSuggestionGenerator(), provider,
        JsonMapper.builder().build(), postRepository, mock(RoomRepository.class), mock(ClassScheduleService.class),
        mock(SensorReadingRepository.class), Clock.systemDefaultZone());
    service.detailWait = Duration.ofMillis(300);
    return service;
  }

  @Test
  void LLM_이_제때_오면_그_답변을_주고_저장한다() {
    when(client.complete(anyString())).thenReturn("[\"네 1층이에요\"]");

    assertThat(service(client).suggestNow(post)).containsExactly("네 1층이에요");
    verify(postRepository).saveSuggestionsIfAbsent(POST_ID, "[\"네 1층이에요\"]");
  }

  @Test
  void LLM_이_늦으면_규칙_기반을_바로_주고_늦게_온_답변은_저장한다() {
    when(client.complete(anyString())).thenAnswer(inv -> {
      Thread.sleep(1_000);
      return "[\"늦은 답\"]";
    });

    long start = System.nanoTime();
    assertThat(service(client).suggestNow(post)).isEqualTo(RULES);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(900));
    verify(postRepository, timeout(3_000)).saveSuggestionsIfAbsent(POST_ID, "[\"늦은 답\"]");
  }

  @Test
  void LLM_이_실패하면_규칙_기반이고_저장하지_않는다() {
    when(client.complete(anyString())).thenThrow(new IllegalStateException("429"));

    assertThat(service(client).suggestNow(post)).isEqualTo(RULES);
    verify(postRepository, never()).saveSuggestionsIfAbsent(anyLong(), anyString());
  }

  @Test
  void 작성_직후_백그라운드에서_만들고_상세는_진행_중인_호출을_같이_기다린다() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    when(client.complete(anyString())).thenAnswer(inv -> {
      calls.incrementAndGet();
      release.await(3, TimeUnit.SECONDS);
      return "[\"미리 만든 답\"]";
    });
    TalkSuggestionService service = service(client);

    service.prepare(POST_ID);
    Thread.sleep(100);
    release.countDown();
    assertThat(service.suggestNow(post)).containsExactly("미리 만든 답");
    assertThat(calls).hasValue(1);
    verify(postRepository, timeout(1_000)).saveSuggestionsIfAbsent(eq(POST_ID), anyString());
  }

  @Test
  void LLM_설정이_없으면_규칙_기반이고_백그라운드도_없다() {
    TalkSuggestionService service = service(null);

    service.prepare(POST_ID);
    assertThat(service.suggestNow(post)).isEqualTo(RULES);
    verify(postRepository, never()).findDetailById(anyLong());
  }
}
