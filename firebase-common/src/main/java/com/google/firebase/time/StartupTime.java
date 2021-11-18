package com.google.firebase.time;

public class StartupTime {
    private final Instant instant;

    public StartupTime(Instant instant) {
        this.instant = instant;
    }

    public Instant getInstant() {
        return instant;
    }
}
