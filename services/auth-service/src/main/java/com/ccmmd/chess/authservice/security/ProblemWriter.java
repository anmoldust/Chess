package com.ccmmd.chess.authservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Writes the platform's RFC 7807 error shape. */
public final class ProblemWriter {

    private ProblemWriter() {
    }

    public static void write(
            ObjectMapper objectMapper, HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), new Problem(status, title, detail));
    }

    private record Problem(int status, String title, String detail) {
    }
}
