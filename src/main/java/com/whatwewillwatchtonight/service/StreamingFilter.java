package com.whatwewillwatchtonight.service;

import java.util.Set;

/**
 * A "pick something streamable" constraint from the frontend.
 *
 * @param region      an ISO-3166-1 country code the availability is checked in
 * @param providerIds TMDB provider ids the group subscribes to
 */
public record StreamingFilter(String region, Set<Integer> providerIds) {
}
