package com.basinwatch.domain;

import java.time.Instant;

public record ActivityEntry(Instant at, String category, String message) {
    @Override
    public String toString() {
        return at.toString().substring(11, 19) + "  " + category + "  " + message;
    }
}
