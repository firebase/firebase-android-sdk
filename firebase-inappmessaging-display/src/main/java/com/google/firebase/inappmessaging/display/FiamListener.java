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

package com.google.firebase.inappmessaging.display;

import androidx.annotation.Keep;

/**
 * Listener interface to be notified of in app messaging events. Use to register your listener.
 *
 * <p>This works as follows
 *
 * <ul>
 *   <li>{@link FiamListener#onFiamTrigger()} is called before the message is rendered. The method
 *       is called repeatedly for a message if it needs to be re-rendered during activity
 *       transitions
 *   <li>{@link FiamListener#onFiamClick()} is called when a message with a configured action is
 *       clicked. If the clicked message does not have a configured action, it is dismissed and
 *       {@link FiamListener#onFiamClick()} is invoked
 *   <li>Called when the message is dismissed either automatically after a timeout or by the user or
 *       when a clicked message has no associated action
 * </ul>
 *
 * @hide
 */
@Keep
public interface FiamListener {
  void onFiamTrigger();

  void onFiamClick();

  void onFiamDismiss();
}
