package com.ccmmd.chess.gateway.auth;

import org.springframework.http.HttpStatusCode;

/** A problem returned by auth-service, carried through so the browser sees the original reason. */
public class DownstreamProblem extends RuntimeException {

    private final HttpStatusCode status;
    private final String title;

    public DownstreamProblem(HttpStatusCode status, String title, String detail) {
        super(detail == null ? title : detail);
        this.status = status;
        this.title = title == null ? "Downstream error" : title;
    }

    public HttpStatusCode status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return getMessage();
    }
}
