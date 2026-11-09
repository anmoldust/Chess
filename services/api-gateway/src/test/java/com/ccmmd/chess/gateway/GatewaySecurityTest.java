package com.ccmmd.chess.gateway;

import com.ccmmd.chess.gateway.auth.AuthServiceClient;
import com.ccmmd.chess.gateway.dto.RegisterRequest;
import com.ccmmd.chess.gateway.dto.TokenResponse;
import com.ccmmd.chess.gateway.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Security behaviour of the browser-facing edge.
 *
 * <p>The CSRF assertions here exist because a hand-rolled verification script once reported a
 * CSRF bypass that did not exist — it was sending a token it believed it had omitted. A test that
 * actually controls what it sends is the only trustworthy regression net for this.
 *
 * <p>Integration test: it needs Redis running, because browser sessions live there. That is
 * deliberate — the {@code SessionUser} serialisation bug that broke session saving was invisible
 * with in-memory sessions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private static String cookie(WebTestClient.ResponseSpec response, String name) {
        ResponseCookie found = response.returnResult(String.class).getResponseCookies().getFirst(name);
        return found == null ? null : found.getValue();
    }

    /** GET that plants the CSRF cookie, returning it. */
    private String csrfToken() {
        WebTestClient.ResponseSpec response = client.get().uri("/api/auth/me").exchange();
        response.expectStatus().isOk();
        String token = cookie(response, "XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(token).as("CSRF cookie must be issued").isNotNull();
        return token;
    }

    @Test
    @DisplayName("the CSRF cookie is readable by JavaScript, otherwise every mutation 403s")
    void csrfCookieIsReadableByJavascript() {
        ResponseCookie csrf = client.get().uri("/api/auth/me").exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseCookies()
                .getFirst("XSRF-TOKEN");

        // The SPA echoes this cookie from document.cookie in X-XSRF-TOKEN. An httpOnly cookie is
        // invisible to JavaScript, so the header is never sent and the gateway rejects the POST.
        assertThat(csrf).as("CSRF cookie must be issued").isNotNull();
        assertThat(csrf.isHttpOnly())
                .as("document.cookie must be able to read the CSRF token")
                .isFalse();
        assertThat(csrf.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("the CSRF token is also returned in a response header, so the SPA never needs the cookie")
    void csrfTokenIsExposedInResponseHeader() {
        FluxExchangeResult<String> result = client.get().uri("/api/auth/me").exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        String header = result.getResponseHeaders().getFirst("X-CSRF-TOKEN");
        ResponseCookie cookie = result.getResponseCookies().getFirst("XSRF-TOKEN");

        assertThat(header).as("response header must carry the CSRF token").isNotNull();
        assertThat(cookie).as("cookie is still issued for compatibility").isNotNull();
        assertThat(header).isEqualTo(cookie.getValue());
    }

    @Test
    @DisplayName("an anonymous caller is reported as unauthenticated, not rejected")
    void anonymousMe() {
        client.get().uri("/api/auth/me").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authenticated").isEqualTo(false);
    }

    @Test
    @DisplayName("an unauthenticated mutation is rejected")
    void anonymousMutationRejected() {
        String token = csrfToken();

        client.post().uri("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .bodyValue("{\"mode\":\"HUMAN_VS_HUMAN\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("a mutation with NO CSRF token is rejected — this is the regression test")
    void mutationWithoutCsrfTokenRejected() {
        csrfToken();

        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"no-csrf@example.com\",\"password\":\"password123\",\"displayName\":\"No Csrf\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("a mutation with a WRONG CSRF token is rejected")
    void mutationWithWrongCsrfTokenRejected() {
        String token = csrfToken();

        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", "not-the-token")
                .bodyValue("{\"email\":\"bad-csrf@example.com\",\"password\":\"password123\",\"displayName\":\"Bad\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("registering establishes a session the browser can then use")
    void registerEstablishesSession() {
        String csrf = csrfToken();
        when(authServiceClient.register(any(RegisterRequest.class))).thenReturn(Mono.just(
                new TokenResponse(new UserDto("user-1", "player@example.com", "Player"), "test-jwt",
                        Instant.now().plusSeconds(900))));

        WebTestClient.ResponseSpec registered = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .bodyValue("{\"email\":\"player@example.com\",\"password\":\"password123\",\"displayName\":\"Player\"}")
                .exchange();

        registered.expectStatus().isCreated();
        String session = cookie(registered, "SESSION");
        org.assertj.core.api.Assertions.assertThat(session).as("session cookie must be set").isNotNull();

        client.get().uri("/api/auth/me")
                .cookie("SESSION", session)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authenticated").isEqualTo(true)
                .jsonPath("$.user.email").isEqualTo("player@example.com");
    }

    @Test
    @DisplayName("logout without a CSRF token is rejected")
    void logoutWithoutCsrfTokenRejected() {
        String csrf = csrfToken();
        when(authServiceClient.register(any(RegisterRequest.class))).thenReturn(Mono.just(
                new TokenResponse(new UserDto("user-2", "bye@example.com", "Bye"), "test-jwt",
                        Instant.now().plusSeconds(900))));

        WebTestClient.ResponseSpec registered = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .bodyValue("{\"email\":\"bye@example.com\",\"password\":\"password123\",\"displayName\":\"Bye\"}")
                .exchange();
        registered.expectStatus().isCreated();
        String session = cookie(registered, "SESSION");

        client.post().uri("/api/auth/logout")
                .cookie("SESSION", session)
                .exchange()
                .expectStatus().isForbidden();
    }
}
