package com.app.sumpyo_be.domain.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 제한 (메모리, 서버 한 대 기준). 같은 IP 에서 10분 안에 실패가 10번을 넘으면 그 IP 의 로그인을 막는다.
 * 성공하면 그 IP 기록을 지운다. 재시작하면 초기화된다.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

  static final int MAX_FAILURES = 10;
  static final Duration WINDOW = Duration.ofMinutes(10);

  private final Clock clock;
  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

  /** 10분 안에 실패가 10번이면 true (11번째 시도부터 막힘) */
  public boolean isBlocked(String ip) {
    Deque<Instant> times = failures.get(ip);
    if (times == null) {
      return false;
    }
    synchronized (times) {
      prune(times);
      return times.size() >= MAX_FAILURES;
    }
  }

  public void recordFailure(String ip) {
    Deque<Instant> times = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
    synchronized (times) {
      prune(times);
      times.addLast(clock.instant());
    }
  }

  public void reset(String ip) {
    failures.remove(ip);
  }

  private void prune(Deque<Instant> times) {
    Instant from = clock.instant().minus(WINDOW);
    while (!times.isEmpty() && times.peekFirst().isBefore(from)) {
      times.pollFirst();
    }
  }
}
