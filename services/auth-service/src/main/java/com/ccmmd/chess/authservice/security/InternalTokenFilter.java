package com.ccmmd.chess.authservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards the token-refresh endpoint with a shared internal secret.
 *
 * <p>The gateway has to mint a token on behalf of a user it has already authenticated, so it
 * cannot present that user's token — obtaining one is the whole point of the call. A service
 * credential is what authorises it. This is deliberately narrow (one endpoint) and is the piece
 * to replace with mTLS or a proper service-identity solution in production.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";
    private static final String GUARDED_PATH = "/api/auth/refresh";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!GUARDED_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedToken)) {
            ProblemWriter.write(objectMapper, response, 401, "Unauthorized",
                    "A valid internal service token is required");
            return;
        }

        chain.doFilter(request, response);
    }
}
