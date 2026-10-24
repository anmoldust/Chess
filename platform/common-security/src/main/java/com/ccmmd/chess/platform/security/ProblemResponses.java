package com.ccmmd.chess.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Writes the same RFC 7807 shape the gateway and services use, so errors look identical. */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static void write(ObjectMapper objectMapper, HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), new Problem(status, title, detail));
    }

    private record Problem(int status, String title, String detail) {
    }
}
