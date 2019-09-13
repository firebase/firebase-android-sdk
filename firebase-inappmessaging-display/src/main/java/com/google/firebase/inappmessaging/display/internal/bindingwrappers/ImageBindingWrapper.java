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

package com.google.firebase.inappmessaging.display.internal.bindingwrappers;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.InAppMessageScope;
import com.google.firebase.inappmessaging.display.internal.layout.FiamFrameLayout;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.ImageOnlyMessage;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import java.util.Map;
import javax.inject.Inject;

/**
 * Wrapper for bindings for Image only modal. This class currently is not unit tested since it is
 * purely declarative.
 *
 * @hide
 */
@InAppMessageScope
public class ImageBindingWrapper extends BindingWrapper {

  private FiamFrameLayout imageRoot;
  private ViewGroup imageContentRoot;

  private ImageView imageView;
  private Button collapseButton;

  @Inject
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public ImageBindingWrapper(
      InAppMessageLayoutConfig config, LayoutInflater inflater, InAppMessage message) {
    super(config, inflater, message);
  }

  @Nullable
  @Override
  public ViewTreeObserver.OnGlobalLayoutListener inflate(
      Map<Action, View.OnClickListener> actionListeners,
      View.OnClickListener dismissOnClickListener) {
    View v = inflater.inflate(R.layout.image, null);
    imageRoot = v.findViewById(R.id.image_root);
    imageContentRoot = v.findViewById(R.id.image_content_root);
    imageView = v.findViewById(R.id.image_view);
    collapseButton = v.findViewById(R.id.collapse_button);

    // Setup ImageView.
    imageView.setMaxHeight(config.getMaxImageHeight());
    imageView.setMaxWidth(config.getMaxImageWidth());
    if (message.getMessageType().equals(MessageType.IMAGE_ONLY)) {
      ImageOnlyMessage msg = (ImageOnlyMessage) message;
      imageView.setVisibility(
          (msg.getImageData() == null || TextUtils.isEmpty(msg.getImageData().getImageUrl()))
              ? View.GONE
              : View.VISIBLE);
      imageView.setOnClickListener(actionListeners.get(msg.getAction()));
    }

    // Setup dismiss button.
    imageRoot.setDismissListener(dismissOnClickListener);
    collapseButton.setOnClickListener(dismissOnClickListener);
    return null;
  }

  @NonNull
  @Override
  public ImageView getImageView() {
    return imageView;
  }

  @NonNull
  @Override
  public ViewGroup getRootView() {
    return imageRoot;
  }

  @NonNull
  @Override
  public View getDialogView() {
    return imageContentRoot;
  }

  @NonNull
  public View getCollapseButton() {
    return collapseButton;
  }
}
