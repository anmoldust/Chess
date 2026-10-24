package com.ccmmd.chess.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** RFC 7807 responses for the reactive entry points, matching what the services emit. */
@Component
public class ReactiveProblems {

    private final ObjectMapper objectMapper;

    public ReactiveProblems(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerHttpResponse response, HttpStatus status, String title, String detail) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new Problem(status.value(), title, detail));
        } catch (JsonProcessingException e) {
            body = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private record Problem(int status, String title, String detail) {
    }
}
