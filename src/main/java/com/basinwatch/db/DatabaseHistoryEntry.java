package com.basinwatch.db;

import java.time.Instant;

public record DatabaseHistoryEntry(
        Instant timestamp,
        String category,
        String zone,
        String details) {
}
