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

package com.google.firebase.inappmessaging.display.internal;

// Picasso 's api forces us to listen to errors only using a global listener set on the picasso
// singleton. Since we initialize picasso from a static context and the in app message param to the
// logError method is not available statically, we are forced to introduce a error listener with
// mutable state so that the error from picasso can be translated to a logError on
// fiam headless, with the in app message as a parameter

import android.net.Uri;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.squareup.picasso.Picasso;
import java.io.IOException;
import javax.inject.Inject;

/** @hide */
@FirebaseAppScope
public class PicassoErrorListener implements Picasso.Listener {
  private InAppMessage inAppMessage;
  private FirebaseInAppMessagingDisplayCallbacks displayCallbacks;

  @Inject
  PicassoErrorListener() {}

  public void setInAppMessage(
      InAppMessage inAppMessage, FirebaseInAppMessagingDisplayCallbacks displayCallbacks) {
    this.inAppMessage = inAppMessage;
    this.displayCallbacks = displayCallbacks;
  }

  @Override
  public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
    if (inAppMessage != null && displayCallbacks != null) {
      if (exception instanceof IOException
          && exception.getLocalizedMessage().contains("Failed to decode")) {
        displayCallbacks.displayErrorEncountered(
            InAppMessagingErrorReason.IMAGE_UNSUPPORTED_FORMAT);
      } else {
        displayCallbacks.displayErrorEncountered(
            InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
      }
    }
  }
}
