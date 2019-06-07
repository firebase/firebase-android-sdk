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
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.Logging;
import com.google.firebase.inappmessaging.model.InAppMessage;

/**
 * View container for all in app message layouts This container serves as an abstraction around
 * bindings created by individual views.
 *
 * @hide
 */
public abstract class BindingWrapper {
  protected final InAppMessage message;
  final InAppMessageLayoutConfig config;
  final LayoutInflater inflater;

  protected BindingWrapper(
      InAppMessageLayoutConfig config, LayoutInflater inflater, InAppMessage message) {
    this.config = config;
    this.inflater = inflater;
    this.message = message;
  }

  @NonNull
  public abstract ImageView getImageView();

  @NonNull
  public abstract ViewGroup getRootView();

  @NonNull
  public abstract View getDialogView();

  @Nullable
  public abstract OnGlobalLayoutListener inflate(
      OnClickListener actionListener, OnClickListener dismissOnClickListener);

  public boolean canSwipeToDismiss() {
    return false;
  }

  @Nullable
  public OnClickListener getDismissListener() {
    return null;
  };

  @NonNull
  public InAppMessageLayoutConfig getConfig() {
    return config;
  }

  protected void setGradientDrawableBgColor(View view, String hexColor) {
    if (view != null && hexColor != null) {
      GradientDrawable layoutBg = (GradientDrawable) view.getBackground();
      try {
        layoutBg.setColor(Color.parseColor(hexColor));
      } catch (IllegalArgumentException e) {
        // If the color didnt parse correctly, fail 'open', with default background color
        Logging.loge("Error parsing background color: " + e.toString());
      }
    }
  }
}
