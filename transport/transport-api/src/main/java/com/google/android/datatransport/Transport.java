// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
   * <p>The event may not be send immediately. It's up to the implementation to define the time when
   * events are actually sent.
   *
   * @param event The event with the payload that needs to be sent.
   */
  void send(Event<T> event);

  /**
   * Schedules sending the event of type T.
   *
   * <p>The {@code callback} tracks the process of persisting the event to permanent storage to
   * ensure it will be scheduled to be send at a later time. It's up to the implementation to define
   * the time when scheduled events are actually sent.
   *
   * @param event The event with the payload that needs to be sent.
   * @param callback The callback to notify during the scheduling process.
   */
  void schedule(Event<T> event, TransportScheduleCallback callback);
}
