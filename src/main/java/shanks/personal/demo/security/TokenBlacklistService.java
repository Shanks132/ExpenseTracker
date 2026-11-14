package shanks.personal.demo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    public void blacklist(String token, long expiryMillis) {
        // store the token in Redis with a TTL matching its remaining expiry
        // so it auto-cleans itself up once it would've expired anyway
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + token,
                "blacklisted",
                expiryMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
