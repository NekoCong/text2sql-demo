package com.nekocong.text2sql.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security (WebFlux) configuration: stateless JWT bearer authentication.
 *
 * <p>Public endpoints: registration, login, the liveness probe and the Actuator endpoints.
 * Everything else requires a valid JWT. The token is read from the {@code Authorization: Bearer ...}
 * header, or from an {@code access_token} URI query parameter (needed because the browser
 * {@code EventSource} used for SSE cannot set headers).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * The security filter chain.
     *
     * @param http the server HTTP security builder
     * @return the filter chain
     */
    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        ServerBearerTokenAuthenticationConverter bearerTokenConverter =
                new ServerBearerTokenAuthenticationConverter();
        // Allow `?access_token=...` so the SSE endpoint works from the browser's EventSource.
        bearerTokenConverter.setAllowUriQueryParameter(true);

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(
                        exchange ->
                                exchange
                                        .pathMatchers(HttpMethod.OPTIONS)
                                        .permitAll()
                                        .pathMatchers(
                                                "/api/auth/register",
                                                "/api/auth/login",
                                                "/api/text2sql/health",
                                                "/actuator/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.bearerTokenConverter(bearerTokenConverter)
                                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * The password encoder.
     *
     * @return a BCrypt encoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The HMAC key used to sign and verify JWTs.
     *
     * @param properties the security configuration
     * @return the secret key
     */
    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        return new SecretKeySpec(
                properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * The JWT encoder.
     *
     * @param key the signing key
     * @return the encoder
     */
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * The JWT decoder used by the resource server.
     *
     * @param key the verification key
     * @return the decoder
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(SecretKey key) {
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
