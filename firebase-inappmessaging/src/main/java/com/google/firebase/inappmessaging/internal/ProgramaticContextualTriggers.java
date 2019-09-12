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

package com.google.firebase.inappmessaging.internal;

/**
 * The {@link ProgramaticContextualTriggers} notifies listeners set via {@link
 * #setListener(Listener)} when an contextual trigger has been programatically triggered via the
 * FirebaseInAppMessaging.triggerEvent() flw.
 *
 * @hide
 */
public class ProgramaticContextualTriggers {
  private Listener listener;

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void removeListener(Listener listener) {
    this.listener = null;
  }

  public void triggerEvent(String eventName) {
    Logging.logd("Programmatically trigger: " + eventName);
    listener.onEventTrigger(eventName);
  }

  /** Listener to receive callbacks when the trigger is emitted */
  public interface Listener {
    void onEventTrigger(String trigger);
  }
}
