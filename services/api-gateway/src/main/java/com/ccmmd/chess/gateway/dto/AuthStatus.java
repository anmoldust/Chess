package com.ccmmd.chess.gateway.dto;

public record AuthStatus(boolean authenticated, UserDto user) {
}
