package com.google.firebase.remoteconfig;

import javax.annotation.Nonnull;

/** Event Listener for Realtime config update callbacks. */
public interface ConfigUpdateListener {
  /**
   * Callback for when a new config has been automatically fetched from the backend. Can be used to
   * activate the new config.
   *
   * @author Quan Pham
   */
  void onEvent();

  /**
   * Call back for when an error occurs during Realtime.
   *
   * @param error
   */
  void onError(@Nonnull Exception error);
}
