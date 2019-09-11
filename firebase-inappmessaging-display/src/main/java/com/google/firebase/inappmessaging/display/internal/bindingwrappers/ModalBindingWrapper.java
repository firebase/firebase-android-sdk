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

import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.InAppMessageScope;
import com.google.firebase.inappmessaging.display.internal.layout.FiamRelativeLayout;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import com.google.firebase.inappmessaging.model.ModalMessage;
import java.util.Map;
import javax.inject.Inject;

/** @hide */
@InAppMessageScope
public class ModalBindingWrapper extends BindingWrapper {

  private FiamRelativeLayout modalRoot;
  private ViewGroup modalContentRoot;

  private ScrollView bodyScroll;
  private Button button;
  private View collapseImage;
  private ImageView imageView;
  private TextView messageBody;
  private TextView messageTitle;
  private ModalMessage modalMessage;

  private ViewTreeObserver.OnGlobalLayoutListener layoutListener =
      new ScrollViewAdjustableListener();

  @Inject
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public ModalBindingWrapper(
      InAppMessageLayoutConfig config, LayoutInflater inflater, InAppMessage message) {
    super(config, inflater, message);
  }

  @NonNull
  @Override
  public ViewTreeObserver.OnGlobalLayoutListener inflate(
      Map<Action, View.OnClickListener> actionListeners,
      View.OnClickListener dismissOnClickListener) {

    View root = inflater.inflate(R.layout.modal, null);
    bodyScroll = root.findViewById(R.id.body_scroll);
    button = root.findViewById(R.id.button);
    collapseImage = root.findViewById(R.id.collapse_button);
    imageView = root.findViewById(R.id.image_view);
    messageBody = root.findViewById(R.id.message_body);
    messageTitle = root.findViewById(R.id.message_title);
    modalRoot = root.findViewById(R.id.modal_root);

    modalContentRoot = root.findViewById(R.id.modal_content_root);

    if (message.getMessageType().equals(MessageType.MODAL)) {
      modalMessage = (ModalMessage) message;
      setMessage(modalMessage);
      setButton(actionListeners);
      setLayoutConfig(config);
      setDismissListener(dismissOnClickListener);
      setViewBgColorFromHex(modalContentRoot, modalMessage.getBackgroundHexColor());
    }
    return layoutListener;
  }

  @NonNull
  @Override
  public ImageView getImageView() {
    return imageView;
  }

  @NonNull
  @Override
  public ViewGroup getRootView() {
    return modalRoot;
  }

  @NonNull
  @Override
  public View getDialogView() {
    return modalContentRoot;
  }

  @NonNull
  public View getScrollView() {
    return bodyScroll;
  }

  @NonNull
  public View getTitleView() {
    return messageTitle;
  }

  @NonNull
  @Override
  public InAppMessageLayoutConfig getConfig() {
    return config;
  }

  @NonNull
  public Button getActionButton() {
    return button;
  }

  @NonNull
  public View getCollapseButton() {
    return collapseImage;
  }

  private void setMessage(ModalMessage message) {
    if (message.getImageData() == null || TextUtils.isEmpty(message.getImageData().getImageUrl())) {
      imageView.setVisibility(View.GONE);
    } else {
      imageView.setVisibility(View.VISIBLE);
    }

    if (message.getTitle() != null) {
      if (!TextUtils.isEmpty(message.getTitle().getText())) {
        messageTitle.setVisibility(View.VISIBLE);
        messageTitle.setText(message.getTitle().getText());
      } else {
        messageTitle.setVisibility(View.GONE);
      }

      if (!TextUtils.isEmpty(message.getTitle().getHexColor())) {
        messageTitle.setTextColor(Color.parseColor(message.getTitle().getHexColor()));
      }
    }

    // eventually we should no longer need to check for the text of the body
    if (message.getBody() != null && !TextUtils.isEmpty(message.getBody().getText())) {
      bodyScroll.setVisibility(View.VISIBLE);
      messageBody.setVisibility(View.VISIBLE);
      messageBody.setTextColor(Color.parseColor(message.getBody().getHexColor()));
      messageBody.setText(message.getBody().getText());
    } else {
      bodyScroll.setVisibility(View.GONE);
      messageBody.setVisibility(View.GONE);
    }
  }

  private void setButton(Map<Action, View.OnClickListener> actionListeners) {
    Action modalAction = modalMessage.getAction();
    // Right now we have to check for text not being empty but this should be fixed in the future
    if (modalAction != null
        && modalAction.getButton() != null
        && !TextUtils.isEmpty(modalAction.getButton().getText().getText())) {
      setupViewButtonFromModel(button, modalAction.getButton());
      setButtonActionListener(button, actionListeners.get(modalMessage.getAction()));
      button.setVisibility(View.VISIBLE);
    } else {
      button.setVisibility(View.GONE);
    }
  }

  private void setLayoutConfig(InAppMessageLayoutConfig config) {
    imageView.setMaxHeight(config.getMaxImageHeight());
    imageView.setMaxWidth(config.getMaxImageWidth());
  }

  private void setDismissListener(View.OnClickListener dismissListener) {
    collapseImage.setOnClickListener(dismissListener);
    modalRoot.setDismissListener(dismissListener);
  }

  @VisibleForTesting
  public void setLayoutListener(ViewTreeObserver.OnGlobalLayoutListener listener) {
    layoutListener = listener;
  }

  // TODO: Kill this.
  public class ScrollViewAdjustableListener implements ViewTreeObserver.OnGlobalLayoutListener {
    @Override
    public void onGlobalLayout() {
      imageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }
  }
}
