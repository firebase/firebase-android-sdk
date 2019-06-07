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
import android.graphics.drawable.Drawable;
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
import androidx.core.graphics.drawable.DrawableCompat;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.InAppMessageScope;
import com.google.firebase.inappmessaging.display.internal.layout.FiamRelativeLayout;
import com.google.firebase.inappmessaging.model.InAppMessage;
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
      View.OnClickListener actionListener, View.OnClickListener dismissOnClickListener) {

    View root = inflater.inflate(R.layout.modal, null);
    bodyScroll = root.findViewById(R.id.body_scroll);
    button = root.findViewById(R.id.button);
    collapseImage = root.findViewById(R.id.collapse_button);
    imageView = root.findViewById(R.id.image_view);
    messageBody = root.findViewById(R.id.message_body);
    messageTitle = root.findViewById(R.id.message_title);
    modalRoot = root.findViewById(R.id.modal_root);

    modalContentRoot = root.findViewById(R.id.modal_content_root);

    setMessage(message);
    setLayoutConfig(config);
    setDismissListener(dismissOnClickListener);
    setActionListener(actionListener);

    setModalColorOverrides();
    setButtonColorOverrides();

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

  private void setMessage(InAppMessage message) {
    if (TextUtils.isEmpty(message.getImageUrl())) {
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

    if (message.getBody() != null && !TextUtils.isEmpty(message.getBody().getText())) {
      bodyScroll.setVisibility(View.VISIBLE);
    } else {
      bodyScroll.setVisibility(View.GONE);
    }

    if (message.getBody() != null) {
      if (!TextUtils.isEmpty(message.getBody().getText())) {
        messageBody.setVisibility(View.VISIBLE);
        messageBody.setText(message.getBody().getText());
      } else {
        messageBody.setVisibility(View.GONE);
      }

      if (!TextUtils.isEmpty(message.getBody().getHexColor())) {
        messageBody.setTextColor(Color.parseColor(message.getBody().getHexColor()));
      }
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

  private void setActionListener(View.OnClickListener actionListener) {
    button.setOnClickListener(actionListener);
  }

  private void setButtonColorOverrides() {
    // Set the background color of the getAction button to be the FIAM color. We do this explicitly
    // to
    // allow for a rounded modal (b/c overloaded background for shape and color)

    if (button != null
        && message.getActionButton() != null
        && message.getActionButton().getButtonHexColor() != null) {
      int buttonColor = Color.parseColor(message.getActionButton().getButtonHexColor());

      // Tint the button based on the background color
      Drawable drawable = button.getBackground();
      Drawable compatDrawable = DrawableCompat.wrap(drawable);
      DrawableCompat.setTint(compatDrawable, buttonColor);
      button.setBackground(compatDrawable);

      if (message.getActionButton() != null && message.getActionButton().getText() != null) {
        if (!TextUtils.isEmpty(message.getActionButton().getText().getText())) {
          button.setVisibility(View.VISIBLE);
          button.setText(message.getActionButton().getText().getText());
        } else {
          button.setVisibility(View.GONE);
        }
        String buttonTextColorStr = message.getActionButton().getText().getHexColor();

        if (!TextUtils.isEmpty(buttonTextColorStr)) {
          button.setTextColor(Color.parseColor(buttonTextColorStr));
        }
      }
    } else {
      button.setVisibility(View.GONE);
    }
  }

  private void setModalColorOverrides() {
    // Set the background color of the Modal to be the FIAM color. We do this explicitly to
    // allow for a rounded modal (b/c overloaded background for shape and color)

    if (modalContentRoot != null) {
      setGradientDrawableBgColor(modalContentRoot, message.getBackgroundHexColor());
    }
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
