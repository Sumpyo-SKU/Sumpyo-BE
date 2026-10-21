package com.app.sumpyo_be.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 API 는 부르지 않는다. 요청 주소·헤더·본문과 응답 파싱만 확인 */
class HttpLlmClientTest {

  private static final String KEY = "test-key";
  private static final String CHAT_RESPONSE =
      "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"[\\\"네\\\"]\"}}]}";

  private record Mocked(HttpLlmClient client, MockRestServiceServer server) {}

  private static Mocked mocked(LlmProvider provider, String model) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    return new Mocked(new HttpLlmClient(provider, KEY, model, builder), server);
  }

  @Test
  void gemini_는_OpenAI_호환_주소에_Bearer_키로_보낸다() {
    Mocked m = mocked(LlmProvider.GEMINI, "gemini-test-model");
    m.server().expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(jsonPath("$.model").value("gemini-test-model"))
        .andExpect(jsonPath("$.messages[0].role").value("user"))
        .andExpect(jsonPath("$.messages[0].content").value("프롬프트"))
        .andRespond(withSuccess(CHAT_RESPONSE, MediaType.APPLICATION_JSON));

    assertThat(m.client().complete("프롬프트")).isEqualTo("[\"네\"]");
    m.server().verify();
  }

  @Test
  void openai() {
    Mocked m = mocked(LlmProvider.OPENAI, "gpt-test-model");
    m.server().expect(requestTo("https://api.openai.com/v1/chat/completions"))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andRespond(withSuccess(CHAT_RESPONSE, MediaType.APPLICATION_JSON));

    assertThat(m.client().complete("프롬프트")).isEqualTo("[\"네\"]");
    m.server().verify();
  }

  @Test
  void anthropic() {
    Mocked m = mocked(LlmProvider.ANTHROPIC, "claude-test-model");
    m.server().expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(header("x-api-key", KEY))
        .andExpect(header("anthropic-version", "2023-06-01"))
        .andExpect(jsonPath("$.max_tokens").value(HttpLlmClient.MAX_TOKENS))
        .andRespond(withSuccess("{\"content\":[{\"type\":\"text\",\"text\":\"[\\\"네\\\"]\"}]}",
            MediaType.APPLICATION_JSON));

    assertThat(m.client().complete("프롬프트")).isEqualTo("[\"네\"]");
    m.server().verify();
  }

  @Test
  void 오류_응답이면_예외_예외_메시지에_키가_없다() {
    Mocked m = mocked(LlmProvider.GEMINI, "gemini-test-model");
    m.server().expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> m.client().complete("프롬프트"))
        .isInstanceOf(RuntimeException.class)
        .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain(KEY));
  }
}
