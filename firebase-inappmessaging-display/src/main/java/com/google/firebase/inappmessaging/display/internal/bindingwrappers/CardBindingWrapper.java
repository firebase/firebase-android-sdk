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
import com.google.firebase.inappmessaging.display.internal.layout.BaseModalLayout;
import com.google.firebase.inappmessaging.display.internal.layout.FiamCardView;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.CardMessage;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import java.util.Map;
import javax.inject.Inject;

/** @hide */
@InAppMessageScope
public class CardBindingWrapper extends BindingWrapper {

  private FiamCardView cardRoot;
  private BaseModalLayout cardContentRoot;
  private ScrollView bodyScroll;
  private Button primaryButton;
  private Button secondaryButton;
  private ImageView imageView;
  private TextView messageBody;
  private TextView messageTitle;
  private CardMessage cardMessage;
  private View.OnClickListener dismissListener;

  private ViewTreeObserver.OnGlobalLayoutListener layoutListener =
      new ScrollViewAdjustableListener();

  @Inject
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public CardBindingWrapper(
      InAppMessageLayoutConfig config, LayoutInflater inflater, InAppMessage message) {
    super(config, inflater, message);
  }

  @NonNull
  @Override
  public ViewTreeObserver.OnGlobalLayoutListener inflate(
      Map<Action, View.OnClickListener> actionListeners,
      View.OnClickListener dismissOnClickListener) {

    View root = inflater.inflate(R.layout.card, null);
    bodyScroll = root.findViewById(R.id.body_scroll);
    primaryButton = root.findViewById(R.id.primary_button);
    secondaryButton = root.findViewById(R.id.secondary_button);
    imageView = root.findViewById(R.id.image_view);
    messageBody = root.findViewById(R.id.message_body);
    messageTitle = root.findViewById(R.id.message_title);
    cardRoot = root.findViewById(R.id.card_root);
    cardContentRoot = root.findViewById(R.id.card_content_root);

    if (message.getMessageType().equals(MessageType.CARD)) {
      cardMessage = (CardMessage) message;
      setMessage(cardMessage);
      setImage(cardMessage);
      setButtons(actionListeners);
      setLayoutConfig(config);
      setDismissListener(dismissOnClickListener);
      setViewBgColorFromHex(cardContentRoot, cardMessage.getBackgroundHexColor());
    }
    return layoutListener;
  }

  @NonNull
  @Override
  public ImageView getImageView() {
    return imageView;
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
  public ViewGroup getRootView() {
    return cardRoot;
  }

  @NonNull
  @Override
  public View getDialogView() {
    return cardContentRoot;
  }

  @NonNull
  @Override
  public InAppMessageLayoutConfig getConfig() {
    return config;
  }

  @NonNull
  @Override
  public View.OnClickListener getDismissListener() {
    return dismissListener;
  }

  @NonNull
  public Button getPrimaryButton() {
    return primaryButton;
  }

  @NonNull
  public Button getSecondaryButton() {
    return secondaryButton;
  }

  private void setMessage(CardMessage message) {
    // We can assume we have a title because the CardMessage model enforces it.
    messageTitle.setText(message.getTitle().getText());
    messageTitle.setTextColor(Color.parseColor(message.getTitle().getHexColor()));

    // Right now we need to check for null, eventually we will make an API change to have hasBody()
    // Additionally right now we have to check for getText. this will be fixed soon.
    if (message.getBody() != null && message.getBody().getText() != null) {
      bodyScroll.setVisibility(View.VISIBLE);
      messageBody.setVisibility(View.VISIBLE);
      messageBody.setText(message.getBody().getText());
      messageBody.setTextColor(Color.parseColor(message.getBody().getHexColor()));
    } else {
      bodyScroll.setVisibility(View.GONE);
      messageBody.setVisibility(View.GONE);
    }
  }

  private void setButtons(Map<Action, View.OnClickListener> actionListeners) {
    Action primaryAction = cardMessage.getPrimaryAction();
    Action secondaryAction = cardMessage.getSecondaryAction();

    // Primary button will always exist.
    setupViewButtonFromModel(primaryButton, primaryAction.getButton());
    // The main display code will override the action listener with a dismiss listener in the case
    // of a missing action url.
    setButtonActionListener(primaryButton, actionListeners.get(primaryAction));
    primaryButton.setVisibility(View.VISIBLE);

    // Secondary button is optional, eventually this null check will be at the model level.
    if (secondaryAction != null && secondaryAction.getButton() != null) {
      setupViewButtonFromModel(secondaryButton, secondaryAction.getButton());
      setButtonActionListener(secondaryButton, actionListeners.get(secondaryAction));
      secondaryButton.setVisibility(View.VISIBLE);
    } else {
      secondaryButton.setVisibility(View.GONE);
    }
  }

  private void setImage(CardMessage message) {
    // Right now we need to check for null, eventually we will make an API change hasImageData()
    if (message.getPortraitImageData() != null || message.getLandscapeImageData() != null) {
      imageView.setVisibility(View.VISIBLE);
    } else {
      imageView.setVisibility(View.GONE);
    }
  }

  private void setLayoutConfig(InAppMessageLayoutConfig config) {
    imageView.setMaxHeight(config.getMaxImageHeight());
    imageView.setMaxWidth(config.getMaxImageWidth());
  }

  private void setDismissListener(View.OnClickListener dismissListener) {
    this.dismissListener = dismissListener;
    cardRoot.setDismissListener(dismissListener);
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
