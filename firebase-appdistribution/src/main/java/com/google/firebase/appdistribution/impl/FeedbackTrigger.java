package com.google.firebase.appdistribution.impl;

public enum FeedbackTrigger {
    NOTIFICATION_FEEDBACK_TRIGGER("notification"),
    CUSTOM_FEEDBACK_TRIGGER("custom");

    private final String value;

    FeedbackTrigger(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
