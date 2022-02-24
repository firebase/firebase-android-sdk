package com.google.firebase.crashlytics.internal.common;

/** Simple, thread-safe, singleton class to keep count of recorded and dropped on-demand events. */
public final class OnDemandCounter {
  private int recordedOnDemandExceptions;
  private int droppedOnDemandExceptions;

  private static final OnDemandCounter INSTANCE;

  private OnDemandCounter() {
    recordedOnDemandExceptions = 0;
    droppedOnDemandExceptions = 0;
  }

  static {
    INSTANCE = new OnDemandCounter();
  }

  public static OnDemandCounter getInstance() {
    return INSTANCE;
  }

  public synchronized int getRecordedOnDemandExceptions() {
    return recordedOnDemandExceptions;
  }

  public synchronized void incrementRecordedOnDemandExceptions() {
    recordedOnDemandExceptions++;
  }

  public synchronized int getDroppedOnDemandExceptions() {
    return droppedOnDemandExceptions;
  }

  public synchronized void incrementDroppedOnDemandExceptions() {
    droppedOnDemandExceptions++;
  }

  public synchronized void resetDroppedOnDemandExceptions() {
    droppedOnDemandExceptions = 0;
  }
}
