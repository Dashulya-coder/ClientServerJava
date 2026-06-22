package http;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;
import java.util.Optional;

public class JwtService {

    private static final long EXPIRATION_MS = 3600_000;

    private final Algorithm algorithm;

    public JwtService(String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public String issueToken(String username) {
        return JWT.create()
                .withSubject(username)
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .sign(algorithm);
    }

    public Optional<String> verify(String token) {
        try {
            String username = JWT.require(algorithm).build().verify(token).getSubject();
            return Optional.of(username);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
