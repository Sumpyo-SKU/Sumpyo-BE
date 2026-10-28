package com.app.sumpyo_be.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 (JWT, HS256). sub = 회원 ID, role, iss = sumpyo, 유효기간 AuthProperties.tokenValidity.
 * 만료·위조·다른 키로 서명된 토큰은 parse 에서 empty. 토큰 값은 로그에 남기지 않는다.
 */
@Component
public class JwtTokenProvider {

  static final String ISSUER = "sumpyo";
  static final String ROLE = "role";

  private final NimbusJwtEncoder encoder;
  private final NimbusJwtDecoder decoder;
  private final Duration validity;
  private final Clock clock;

  public JwtTokenProvider(AuthProperties props, Clock clock) {
    SecretKey key = new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
    timestamp.setClock(clock);
    this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(ISSUER)));
    this.validity = props.tokenValidity();
    this.clock = clock;
  }

  public String issue(Long memberId, String role) {
    Instant now = clock.instant();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(ISSUER)
        .subject(String.valueOf(memberId))
        .issuedAt(now)
        .expiresAt(now.plus(validity))
        .claim(ROLE, role)
        .build();
    return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  /** 유효기간 (초). 로그인 응답의 expiresIn */
  public long validitySeconds() {
    return validity.toSeconds();
  }

  /** 유효한 토큰이면 회원, 만료·위조·형식 오류면 empty */
  public Optional<MemberPrincipal> parse(String token) {
    try {
      Jwt jwt = decoder.decode(token);
      return Optional.of(new MemberPrincipal(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(ROLE)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
