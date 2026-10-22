package com.app.sumpyo_be.domain.outdoor.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import com.app.sumpyo_be.domain.outdoor.OutdoorProperties;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient.ForecastSlot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 공공데이터포털 응답 파싱 (실제 API 는 부르지 않는다) */
class DataGoKrOutdoorClientTest {

  /** + / = 가 들어간 Decoding 키 → 쿼리에서 인코딩돼야 한다 */
  private static final String KEY = "ab+c/d==";
  private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 22, 10, 0);

  private MockRestServiceServer server;
  private DataGoKrOutdoorClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new DataGoKrOutdoorClient(new OutdoorProperties(KEY, "60", "127", "정릉로"),
        JsonMapper.builder().build(), builder);
  }

  @Test
  void 초단기실황_기온_습도와_키_인코딩() {
    server.expect(requestTo(startsWith("https://apis.data.go.kr" + DataGoKrOutdoorClient.NOWCAST_PATH)))
        .andExpect(queryParam("serviceKey", "ab%2Bc%2Fd%3D%3D"))
        .andExpect(queryParam("base_date", "20261022"))
        .andExpect(queryParam("base_time", "1000"))
        .andExpect(queryParam("nx", "60"))
        .andRespond(withSuccess("""
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[
               {"category":"PTY","obsrValue":"0"},
               {"category":"REH","obsrValue":"62"},
               {"category":"T1H","obsrValue":"18.0"}]}}}}""", MediaType.APPLICATION_JSON));

    assertThat(client.nowcast(BASE)).contains(new OutdoorApiClient.Nowcast(new BigDecimal("18.0"), 62));
    server.verify();
  }

  @Test
  void 초단기실황_발표_전_NO_DATA_면_empty() {
    server.expect(requestTo(startsWith("https://apis.data.go.kr" + DataGoKrOutdoorClient.NOWCAST_PATH)))
        .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NO_DATA\"}}}",
            MediaType.APPLICATION_JSON));
    assertThat(client.nowcast(BASE)).isEmpty();
  }

  @Test
  void 키가_틀려_XML_오류가_오면_예외() {
    server.expect(requestTo(startsWith("https://apis.data.go.kr" + DataGoKrOutdoorClient.NOWCAST_PATH)))
        .andRespond(withSuccess("<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
            + "</cmmMsgHeader></OpenAPI_ServiceResponse>", MediaType.TEXT_XML));
    assertThatThrownBy(() -> client.nowcast(BASE)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 초단기예보_강수형태만_시각순() {
    server.expect(requestTo(startsWith("https://apis.data.go.kr" + DataGoKrOutdoorClient.FORECAST_PATH)))
        .andExpect(queryParam("base_time", "0930"))
        .andRespond(withSuccess("""
            {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
              {"category":"PTY","fcstDate":"20261022","fcstTime":"1100","fcstValue":"1"},
              {"category":"T1H","fcstDate":"20261022","fcstTime":"1000","fcstValue":"18"},
              {"category":"PTY","fcstDate":"20261022","fcstTime":"1000","fcstValue":"0"}]}}}}""",
            MediaType.APPLICATION_JSON));

    assertThat(client.forecast(BASE.minusMinutes(30))).containsExactly(
        new ForecastSlot(LocalDateTime.of(2026, 10, 22, 10, 0), 0),
        new ForecastSlot(LocalDateTime.of(2026, 10, 22, 11, 0), 1));
  }

  @Test
  void 에어코리아_pm10_점검_중이면_empty() {
    server.expect(requestTo(startsWith("https://apis.data.go.kr" + DataGoKrOutdoorClient.AIR_PATH)))
        .andExpect(queryParam("stationName", "%EC%A0%95%EB%A6%89%EB%A1%9C"))
        .andRespond(withSuccess("""
            {"response":{"header":{"resultCode":"00"},"body":{"items":[
              {"dataTime":"2026-10-22 10:00","pm10Value":"28"}]}}}""", MediaType.APPLICATION_JSON));
    server.expect(requestTo(startsWith("https://apis.data.go.kr" + DataGoKrOutdoorClient.AIR_PATH)))
        .andRespond(withSuccess("""
            {"response":{"header":{"resultCode":"00"},"body":{"items":[
              {"dataTime":"2026-10-22 10:00","pm10Value":"-"}]}}}""", MediaType.APPLICATION_JSON));

    assertThat(client.pm10()).contains(28);
    assertThat(client.pm10()).isEmpty();
  }
}
