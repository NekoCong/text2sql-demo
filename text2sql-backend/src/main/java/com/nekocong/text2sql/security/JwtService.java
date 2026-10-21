package com.nekocong.text2sql.security;

import com.nekocong.text2sql.config.SecurityProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues signed JWTs for authenticated users.
 *
 * <p>The token subject is the user id; a {@code username} claim is included for convenience.
 */
@Component
public class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;

    /**
     * Creates the service.
     *
     * @param encoder the JWT encoder
     * @param properties the security configuration
     */
    public JwtService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * Issues an access token for a user.
     *
     * @param userId the user id (token subject)
     * @param username the username claim
     * @return the compact JWT
     */
    public String issue(String userId, String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(properties.getIssuer())
                        .subject(userId)
                        .claim("username", username)
                        .issuedAt(now)
                        .expiresAt(now.plus(properties.getTokenTtlMinutes(), ChronoUnit.MINUTES))
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Returns the access-token lifetime in seconds.
     *
     * @return the TTL in seconds
     */
    public long ttlSeconds() {
        return properties.getTokenTtlMinutes() * 60;
    }
}
