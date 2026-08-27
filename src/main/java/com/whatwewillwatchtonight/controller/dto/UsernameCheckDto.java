package com.whatwewillwatchtonight.controller.dto;

public record UsernameCheckDto(boolean exists, boolean watchlistPublic, String avatarUrl) {
}
