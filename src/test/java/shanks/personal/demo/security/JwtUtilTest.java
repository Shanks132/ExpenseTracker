package shanks.personal.demo.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shanks.personal.demo.security.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-that-is-longer-than-32-bytes";
    private static final long ONE_HOUR = 3_600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = newJwtUtil(SECRET, ONE_HOUR);
    }

    private static JwtUtil newJwtUtil(String secret, long expirationMillis) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", expirationMillis);
        return util;
    }

    @Test
    void generatedTokenCarriesUsernameAndUserId() {
        String token = jwtUtil.generateToken("alice", 42L);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void validateTokenAcceptsTheMatchingUsername() {
        String token = jwtUtil.generateToken("alice", 1L);

        assertThat(jwtUtil.validateToken(token, "alice")).isTrue();
    }

    @Test
    void validateTokenRejectsADifferentUsername() {
        String token = jwtUtil.generateToken("alice", 1L);

        assertThat(jwtUtil.validateToken(token, "bob")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        // A negative lifetime produces a token that expired a second ago.
        String expired = newJwtUtil(SECRET, -1_000L).generateToken("alice", 1L);

        assertThatThrownBy(() -> jwtUtil.extractUsername(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtUtil attacker = newJwtUtil("a-completely-different-secret-key-of-enough-length", ONE_HOUR);
        String forged = attacker.generateToken("alice", 1L);

        assertThatThrownBy(() -> jwtUtil.extractUsername(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = jwtUtil.generateToken("alice", 1L);
        String[] parts = token.split("\\.");
        String evilPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\",\"userId\":1}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + evilPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtUtil.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> jwtUtil.extractUsername("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void remainingExpiryIsPositiveAndNoMoreThanTheConfiguredLifetime() {
        String token = jwtUtil.generateToken("alice", 1L);

        assertThat(jwtUtil.getRemainingExpiry(token))
                .isPositive()
                .isLessThanOrEqualTo(ONE_HOUR);
    }
}
