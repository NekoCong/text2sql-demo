package com.nekocong.text2sql.controller;

import com.nekocong.text2sql.controller.dto.AuthResponse;
import com.nekocong.text2sql.controller.dto.LoginRequest;
import com.nekocong.text2sql.controller.dto.RegisterRequest;
import com.nekocong.text2sql.controller.dto.UserView;
import com.nekocong.text2sql.service.AuthResult;
import com.nekocong.text2sql.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controller layer for authentication. Delegates all business logic to {@link AuthService}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Creates the controller.
     *
     * @param authService the authentication service
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user and returns a token.
     *
     * @param request the registration request
     * @return the auth response
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@RequestBody RegisterRequest request) {
        return authService
                .register(request.username(), request.password(), request.displayName())
                .map(AuthController::toResponse);
    }

    /**
     * Authenticates a user and returns a token.
     *
     * @param request the login request
     * @return the auth response
     */
    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password()).map(AuthController::toResponse);
    }

    /**
     * Returns the currently authenticated user.
     *
     * @param jwt the bearer token
     * @return the user
     */
    @GetMapping("/me")
    public Mono<UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return authService
                .requireUser(jwt.getSubject())
                .map(user -> new UserView(user.id(), user.username(), user.displayName()));
    }

    private static AuthResponse toResponse(AuthResult result) {
        return new AuthResponse(
                result.token(),
                result.expiresIn(),
                new UserView(result.userId(), result.username(), result.displayName()));
    }
}
