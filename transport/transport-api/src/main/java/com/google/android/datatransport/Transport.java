package com.google.android.datatransport;

/**
 * Provides means of recording events of interest.
 *
 * <p>Event destination is unspecified and depends on concrete implementations of this interface.
 */
public interface Transport<T> {
  /**
   * Sends the event of type T.
   *
   * @param event The event with the payload that needs to be sent.
   */
  void send(Event<T> event);
}
