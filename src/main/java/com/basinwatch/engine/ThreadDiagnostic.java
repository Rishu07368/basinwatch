package com.basinwatch.engine;

public record ThreadDiagnostic(String name, String state, int priority, boolean alive) {
}
