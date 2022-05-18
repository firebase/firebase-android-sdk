package com.google.firebase.remoteconfig;

/** Event Listener for Realtime config update callbacks. */
public interface ConfigUpdateListener {
  /**
   * Callback for when a new config has been automatically fetched from the backend. Can be used to
   * activate the new config.
   */
  void onEvent();

  /**
   * Call back for when an error occurs during Realtime.
   *
   * @param error
   */
  void onError(Exception error);
}
