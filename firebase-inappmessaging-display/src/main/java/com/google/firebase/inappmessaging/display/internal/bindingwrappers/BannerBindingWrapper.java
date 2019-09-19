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
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.firebase.inappmessaging.display.R;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.ResizableImageView;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.InAppMessageScope;
import com.google.firebase.inappmessaging.display.internal.layout.FiamFrameLayout;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import java.util.Map;
import javax.inject.Inject;

/** @hide */
@InAppMessageScope
@SuppressWarnings("Convert2Lambda")
public class BannerBindingWrapper extends BindingWrapper {

  private FiamFrameLayout bannerRoot;
  private ViewGroup bannerContentRoot;

  private TextView bannerBody;
  private ResizableImageView bannerImage;
  private TextView bannerTitle;

  private View.OnClickListener mDismissListener;

  @Inject
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public BannerBindingWrapper(
      InAppMessageLayoutConfig config, LayoutInflater inflater, InAppMessage message) {
    super(config, inflater, message);
  }

  @Nullable
  @Override
  public ViewTreeObserver.OnGlobalLayoutListener inflate(
      Map<Action, View.OnClickListener> actionListeners,
      View.OnClickListener dismissOnClickListener) {

    View root = inflater.inflate(R.layout.banner, null);
    bannerRoot = root.findViewById(R.id.banner_root);
    bannerContentRoot = root.findViewById(R.id.banner_content_root);
    bannerBody = root.findViewById(R.id.banner_body);
    bannerImage = root.findViewById(R.id.banner_image);
    bannerTitle = root.findViewById(R.id.banner_title);

    if (message.getMessageType().equals(MessageType.BANNER)) {
      BannerMessage bannerMessage = (BannerMessage) message;
      setMessage(bannerMessage);
      setLayoutConfig(config);
      setSwipeDismissListener(dismissOnClickListener);
      setActionListener(actionListeners.get(bannerMessage.getAction()));
    }
    return null;
  }

  private void setMessage(@NonNull BannerMessage message) {
    if (!TextUtils.isEmpty(message.getBackgroundHexColor())) {
      setViewBgColorFromHex(bannerContentRoot, message.getBackgroundHexColor());
    }

    bannerImage.setVisibility(
        (message.getImageData() == null || TextUtils.isEmpty(message.getImageData().getImageUrl()))
            ? View.GONE
            : View.VISIBLE);

    if (message.getTitle() != null) {
      if (!TextUtils.isEmpty(message.getTitle().getText())) {
        bannerTitle.setText(message.getTitle().getText());
      }

      if (!TextUtils.isEmpty(message.getTitle().getHexColor())) {
        bannerTitle.setTextColor(Color.parseColor(message.getTitle().getHexColor()));
      }
    }

    if (message.getBody() != null) {
      if (!TextUtils.isEmpty(message.getBody().getText())) {
        bannerBody.setText(message.getBody().getText());
      }

      if (!TextUtils.isEmpty(message.getBody().getHexColor())) {
        bannerBody.setTextColor(Color.parseColor(message.getBody().getHexColor()));
      }
    }
  }

  private void setLayoutConfig(InAppMessageLayoutConfig layoutConfig) {
    // TODO: Document why the width is the min of the max width and height
    int bannerWidth = Math.min(layoutConfig.maxDialogWidthPx(), layoutConfig.maxDialogHeightPx());

    ViewGroup.LayoutParams params = bannerRoot.getLayoutParams();
    if (params == null) {
      params =
          new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    params.width = bannerWidth;

    bannerRoot.setLayoutParams(params);

    bannerImage.setMaxHeight(layoutConfig.getMaxImageHeight());
    bannerImage.setMaxWidth(layoutConfig.getMaxImageWidth());
  }

  private void setSwipeDismissListener(final View.OnClickListener dismissListener) {
    mDismissListener = dismissListener;
    bannerRoot.setDismissListener(mDismissListener);
  }

  private void setActionListener(View.OnClickListener actionListener) {
    bannerContentRoot.setOnClickListener(actionListener);
  }

  @NonNull
  @Override
  public InAppMessageLayoutConfig getConfig() {
    return config;
  }

  @NonNull
  @Override
  public ImageView getImageView() {
    return bannerImage;
  }

  @NonNull
  @Override
  public ViewGroup getRootView() {
    return bannerRoot;
  }

  @NonNull
  @Override
  public View getDialogView() {
    return bannerContentRoot;
  }

  @Nullable
  @Override
  public View.OnClickListener getDismissListener() {
    return mDismissListener;
  }

  @Override
  public boolean canSwipeToDismiss() {
    return true;
  }
}
