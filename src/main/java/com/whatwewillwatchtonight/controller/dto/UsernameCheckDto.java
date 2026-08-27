package com.watchlistintersector.controller.dto;

public record UsernameCheckDto(boolean exists, boolean watchlistPublic, String avatarUrl) {
}
