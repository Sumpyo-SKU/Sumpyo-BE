package com.app.sumpyo_be.domain.outdoor.client;

import com.app.sumpyo_be.domain.outdoor.OutdoorProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공데이터포털 호출.
 * - 기상청 단기예보 조회서비스: 초단기실황(getUltraSrtNcst) T1H·REH, 초단기예보(getUltraSrtFcst) PTY
 * - 에어코리아 대기오염정보: 측정소별 실시간 측정정보(getMsrstnAcctoRltmMesureDnsty) pm10Value
 * serviceKey 는 URI 변수로 넘겨 + / = 까지 인코딩된다 (Decoding 키를 그대로 쓰면 됨). 키는 로그에 남기지 않는다.
 */
@Component
public class DataGoKrOutdoorClient implements OutdoorApiClient {

  static final String BASE_URL = "https://apis.data.go.kr";
  static final String NOWCAST_PATH = "/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
  static final String FORECAST_PATH = "/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst";
  static final String AIR_PATH = "/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");

  private final OutdoorProperties props;
  private final JsonMapper jsonMapper;
  private final RestClient restClient;

  @Autowired
  public DataGoKrOutdoorClient(OutdoorProperties props, JsonMapper jsonMapper) {
    this(props, jsonMapper, RestClient.builder().requestFactory(requestFactory()));
  }

  /** 테스트에서 MockRestServiceServer 를 붙이려고 builder 를 받는다 */
  DataGoKrOutdoorClient(OutdoorProperties props, JsonMapper jsonMapper, RestClient.Builder builder) {
    this.props = props;
    this.jsonMapper = jsonMapper;
    this.restClient = builder.baseUrl(BASE_URL).build();
  }

  private static SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT);
    factory.setReadTimeout(TIMEOUT);
    return factory;
  }

  @Override
  public Optional<Nowcast> nowcast(LocalDateTime baseAt) {
    BigDecimal temp = null;
    Integer humidity = null;
    for (JsonNode item : kmaItems(NOWCAST_PATH, baseAt, 10)) {
      String value = item.path("obsrValue").asString();
      switch (item.path("category").asString()) {
        case "T1H" -> temp = new BigDecimal(value);
        case "REH" -> humidity = (int) Math.round(Double.parseDouble(value));
        default -> { }
      }
    }
    return temp == null && humidity == null ? Optional.empty() : Optional.of(new Nowcast(temp, humidity));
  }

  @Override
  public List<ForecastSlot> forecast(LocalDateTime baseAt) {
    Map<LocalDateTime, Integer> pty = new TreeMap<>();
    for (JsonNode item : kmaItems(FORECAST_PATH, baseAt, 60)) {
      if ("PTY".equals(item.path("category").asString())) {
        LocalDateTime at = LocalDate.parse(item.path("fcstDate").asString(), YYYYMMDD)
            .atTime(LocalTime.parse(item.path("fcstTime").asString(), HHMM));
        pty.put(at, Integer.parseInt(item.path("fcstValue").asString()));
      }
    }
    List<ForecastSlot> slots = new ArrayList<>();
    pty.forEach((at, value) -> slots.add(new ForecastSlot(at, value)));
    return slots;
  }

  @Override
  public Optional<Integer> pm10() {
    String body = restClient.get()
        .uri(AIR_PATH + "?serviceKey={key}&returnType=json&numOfRows=1&pageNo=1&stationName={station}"
            + "&dataTerm=DAILY&ver=1.3", Map.of("key", props.apiKey().strip(), "station", props.station().strip()))
        .retrieve().body(String.class);
    JsonNode response = read(body).path("response");
    requireOk(response);
    JsonNode items = response.path("body").path("items");
    if (!items.isArray() || items.isEmpty()) {
      return Optional.empty();
    }
    String value = items.get(0).path("pm10Value").asString("");
    return value.matches("\\d+") ? Optional.of(Integer.parseInt(value)) : Optional.empty();   // 점검 중이면 "-"
  }

  /** 기상청 응답의 item 목록. 자료 없음(NO_DATA)이면 빈 목록 */
  private List<JsonNode> kmaItems(String path, LocalDateTime baseAt, int rows) {
    String body = restClient.get()
        .uri(path + "?serviceKey={key}&pageNo=1&numOfRows={rows}&dataType=JSON&base_date={date}&base_time={time}"
                + "&nx={nx}&ny={ny}",
            Map.of("key", props.apiKey().strip(), "rows", rows, "date", baseAt.format(YYYYMMDD),
                "time", baseAt.format(HHMM), "nx", props.nx().strip(), "ny", props.ny().strip()))
        .retrieve().body(String.class);
    JsonNode response = read(body).path("response");
    if ("03".equals(response.path("header").path("resultCode").asString())) {   // NO_DATA: 아직 발표 전
      return List.of();
    }
    requireOk(response);
    List<JsonNode> items = new ArrayList<>();
    response.path("body").path("items").path("item").forEach(items::add);
    return items;
  }

  /** 키가 틀리면 XML 오류가 오기도 해서 JSON 이 아니면 실패로 본다 */
  private JsonNode read(String body) {
    if (body == null || !body.stripLeading().startsWith("{")) {
      throw new IllegalStateException("JSON 이 아닌 응답");
    }
    return jsonMapper.readTree(body);
  }

  private static void requireOk(JsonNode response) {
    String code = response.path("header").path("resultCode").asString("");
    if (!"00".equals(code)) {
      throw new IllegalStateException("resultCode " + code);
    }
  }
}
