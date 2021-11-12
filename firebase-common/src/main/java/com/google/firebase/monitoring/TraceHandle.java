package com.google.firebase.monitoring;

import java.io.Closeable;

public interface TraceHandle extends Closeable {
  TraceHandle NOOP =
      new TraceHandle() {
        @Override
        public void addAttribute(String name, String value) {}

        @Override
        public void close() {}
      };

  void addAttribute(String name, String value);

  @Override
  void close();
}
