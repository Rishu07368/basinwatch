package com.basinwatch.engine;

import com.basinwatch.domain.BasinEvent;

import java.util.ArrayDeque;
import java.util.List;

public final class EventBuffer {
    private final ArrayDeque<BasinEvent> events = new ArrayDeque<>();
    private final int capacity;
    private boolean closed;
    private int waitingConsumers;
    private int waitingProducers;
    private int activeConsumers;

    public EventBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Event buffer capacity must be positive.");
        }
        this.capacity = capacity;
    }

    public synchronized boolean put(BasinEvent event) throws InterruptedException {
        if (event == null) {
            throw new IllegalArgumentException("Event is required.");
        }
        while (!closed && events.size() == capacity) {
            waitingProducers++;
            try {
                wait();
            } finally {
                waitingProducers--;
            }
        }
        if (closed) {
            return false;
        }
        events.addLast(event);
        notifyAll();
        return true;
    }

    public synchronized boolean offerAll(List<? extends BasinEvent> batch) {
        if (batch == null || batch.stream().anyMatch(event -> event == null)) {
            throw new IllegalArgumentException("Event batch must contain valid events.");
        }
        if (closed || batch.size() > capacity - events.size()) {
            return false;
        }
        events.addAll(batch);
        notifyAll();
        return true;
    }

    public synchronized BasinEvent take() throws InterruptedException {
        while (events.isEmpty() && !closed) {
            waitingConsumers++;
            try {
                wait();
            } finally {
                waitingConsumers--;
            }
        }
        if (events.isEmpty()) {
            return null;
        }
        BasinEvent event = events.removeFirst();
        activeConsumers++;
        notifyAll();
        return event;
    }

    public synchronized void complete() {
        if (activeConsumers < 1) {
            throw new IllegalStateException("No event is currently being processed.");
        }
        activeConsumers--;
        notifyAll();
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized int capacity() {
        return capacity;
    }

    public synchronized int waitingConsumers() {
        return waitingConsumers;
    }

    public synchronized int waitingProducers() {
        return waitingProducers;
    }

    public synchronized int activeConsumers() {
        return activeConsumers;
    }

    public synchronized boolean isClosed() {
        return closed;
    }
}
