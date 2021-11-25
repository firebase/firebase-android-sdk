package com.google.firebase.testing.integ;

import org.junit.internal.Throwables;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class MultipleViolationsException extends Exception {
  private final List<Throwable> errors;

  private MultipleViolationsException(List<Throwable> errors) {
    this.errors = new ArrayList<>(errors);
  }

  @Override
  public String getMessage() {
    StringBuilder sb = new StringBuilder("There were " + errors.size() + " errors:");
    for (Throwable e : errors) {
      sb.append(String.format("%n  %s(%s)", e.getClass().getName(), e.getMessage()));
    }
    return sb.toString();
  }

  @Override
  public void printStackTrace() {
    for (Throwable e : errors) {
      e.printStackTrace();
    }
  }

  @Override
  public void printStackTrace(PrintStream s) {
    for (Throwable e : errors) {
      e.printStackTrace(s);
    }
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    for (Throwable e : errors) {
      e.printStackTrace(s);
    }
  }

  public static void assertEmpty(List<Throwable> errors) throws Exception {
    if (errors.isEmpty()) {
      return;
    }
    if (errors.size() == 1) {
      throw Throwables.rethrowAsException(errors.get(0));
    }
    throw new MultipleViolationsException(errors);
  }
}
