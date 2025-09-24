package com.google.firebase.gradle.plugins.report;

public record TestReport(String name, Type type, Status status, String commit, String url) {

  public enum Type {
    UNIT_TEST,
    INSTRUMENTATION_TEST
  }

  public enum Status {
    SUCCESS,
    FAILURE,
    OTHER
  }
}
