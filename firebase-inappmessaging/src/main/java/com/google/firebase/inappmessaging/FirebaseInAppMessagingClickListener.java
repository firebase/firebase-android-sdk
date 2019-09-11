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

package com.google.firebase.inappmessaging;

import androidx.annotation.NonNull;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.InAppMessage;

public interface FirebaseInAppMessagingClickListener {

  // Triggered when a message is tapped (ie: button, in the modal view) and which action was
  // triggered (which provides support for the multi-button templates)
  void messageClicked(@NonNull InAppMessage inAppMessage, @NonNull Action action);
}
