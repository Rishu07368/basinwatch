package com.basinwatch.engine;

import java.util.List;

public record EngineDiagnostics(
        int queuedEvents,
        int queueCapacity,
        int waitingConsumers,
        int waitingProducers,
        int activeEvents,
        int pendingArchiveRecords,
        long processedEvents,
        String status,
        List<ThreadDiagnostic> threads) {
    public EngineDiagnostics {
        threads = List.copyOf(threads);
    }
}
