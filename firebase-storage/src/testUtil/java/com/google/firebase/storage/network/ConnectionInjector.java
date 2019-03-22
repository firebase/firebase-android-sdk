package com.google.firebase.storage.network;

import java.io.IOException;

/**
 * Interface used to inject exceptions during test runs. Implementing classes need to override
 * `injectInputStream` and `injectOutputStream` which can then throw IOExceptions based on the range
 * provided via the arguments.
 */
public interface ConnectionInjector {
  void injectInputStream(int start, int end) throws IOException;

  void injectOutputStream(int start, int end) throws IOException;
}
