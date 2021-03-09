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

import androidx.annotation.Nullable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.model.InAppMessage;
import javax.inject.Inject;

/** @hide */
@FirebaseAppScope
public class GlideErrorListener implements RequestListener<Object> {
  private InAppMessage inAppMessage;
  private FirebaseInAppMessagingDisplayCallbacks displayCallbacks;

  @Inject
  GlideErrorListener() {}

  public void setInAppMessage(
      InAppMessage inAppMessage, FirebaseInAppMessagingDisplayCallbacks displayCallbacks) {
    this.inAppMessage = inAppMessage;
    this.displayCallbacks = displayCallbacks;
  }

  @Override
  public boolean onLoadFailed(
      @Nullable GlideException e, Object model, Target<Object> target, boolean isFirstResource) {
    Logging.logd("Image Downloading  Error : " + e.getMessage() + ":" + e.getCause());

    if (inAppMessage != null && displayCallbacks != null) {
      if (e.getLocalizedMessage().contains("Failed to decode")) {
        displayCallbacks.displayErrorEncountered(
            FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason
                .IMAGE_UNSUPPORTED_FORMAT);
      } else {
        displayCallbacks.displayErrorEncountered(
            FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason
                .UNSPECIFIED_RENDER_ERROR);
      }
    }

    return false;
  }

  @Override
  public boolean onResourceReady(
      Object resource,
      Object model,
      Target<Object> target,
      DataSource dataSource,
      boolean isFirstResource) {
    Logging.logd("Image Downloading  Success : " + resource);
    return false;
  }
}
