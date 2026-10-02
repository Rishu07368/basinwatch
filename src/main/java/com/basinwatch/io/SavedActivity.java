package com.basinwatch.io;

import java.io.Serial;
import java.io.Serializable;

final class SavedActivity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    long epochMillis;
    String category;
    String message;

    SavedActivity() {
    }

    SavedActivity(long epochMillis, String category, String message) {
        this.epochMillis = epochMillis;
        this.category = category;
        this.message = message;
    }
}
