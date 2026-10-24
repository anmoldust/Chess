package com.ccmmd.chess.dto;

public record AuthStatusDto(boolean authenticated, UserDto user) {
}
