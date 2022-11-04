package com.google.firebase.concurrent;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

class CustomThreadFactory implements ThreadFactory {
  private static final ThreadFactory DEFAULT = Executors.defaultThreadFactory();
  private final AtomicLong threadCount = new AtomicLong();
  private final String namePrefix;
  private final int priority;

  CustomThreadFactory(String namePrefix, int priority) {
    this.namePrefix = namePrefix;
    this.priority = priority;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread thread = DEFAULT.newThread(r);
    thread.setPriority(priority);
    thread.setName(
        String.format(Locale.ROOT, "%s Thread #%d", namePrefix, threadCount.getAndIncrement()));
    return thread;
  }
}
