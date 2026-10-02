package com.basinwatch.domain;

public interface BasinEvent {
    long tick();

    String zoneId();

    String category();

    String describe();
}
