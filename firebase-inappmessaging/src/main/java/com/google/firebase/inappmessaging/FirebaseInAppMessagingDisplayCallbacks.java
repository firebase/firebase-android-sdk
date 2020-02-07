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
import com.google.android.gms.tasks.Task;
import com.google.firebase.inappmessaging.model.Action;

public interface FirebaseInAppMessagingDisplayCallbacks {

  // log the campaign impression:
  @NonNull
  Task<Void> impressionDetected();

  // log when a message is dismissed, and specify dismiss type
  @NonNull
  Task<Void> messageDismissed(@NonNull InAppMessagingDismissType dismissType);

  // log when a message is tap (ie: button, in the modal view)  with the Action followed
  @NonNull
  Task<Void> messageClicked(@NonNull Action action);

  // log when there is an issue rendering the content (ie, image_url is invalid
  // or file_type is unsupported
  @NonNull
  Task<Void> displayErrorEncountered(@NonNull InAppMessagingErrorReason inAppMessagingErrorReason);

  enum InAppMessagingDismissType {
    // Unspecified dismiss type
    UNKNOWN_DISMISS_TYPE,

    // Message was dismissed automatically after a timeout
    AUTO,

    // Message was dismissed by clicking on cancel button or outside the message
    CLICK,

    // Message was swiped
    SWIPE
  }

  enum InAppMessagingErrorReason {
    // Generic error
    UNSPECIFIED_RENDER_ERROR,

    // Failure to fetch the image
    IMAGE_FETCH_ERROR,

    // Failure to display the image
    IMAGE_DISPLAY_ERROR,

    // Image has an unsupported format
    IMAGE_UNSUPPORTED_FORMAT
  }
}
