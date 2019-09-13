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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Configurations for in app message layouts
 *
 * @hide
 */
public class InAppMessageLayoutConfig {

  private Float maxImageHeightWeight;
  private Float maxImageWidthWeight;
  private Float maxBodyHeightWeight;
  private Float maxBodyWidthWeight;
  private Integer maxDialogHeightPx;
  private Integer maxDialogWidthPx;
  private Integer windowFlag;
  private Integer viewWindowGravity;
  private Integer windowWidth;
  private Integer windowHeight;
  private Boolean backgroundEnabled;
  private Boolean animate;
  private Boolean autoDismiss;

  @NonNull
  public static Builder builder() {
    return new Builder();
  }

  public Float maxImageHeightWeight() {
    return maxImageHeightWeight;
  }

  public Float maxImageWidthWeight() {
    return maxImageWidthWeight;
  }

  @Nullable
  public Float maxBodyHeightWeight() {
    return maxBodyHeightWeight;
  }

  @Nullable
  public Float maxBodyWidthWeight() {
    return maxBodyWidthWeight;
  }

  public Integer maxDialogHeightPx() {
    return maxDialogHeightPx;
  }

  public Integer maxDialogWidthPx() {
    return maxDialogWidthPx;
  }

  public Integer windowFlag() {
    return windowFlag;
  }

  public Integer viewWindowGravity() {
    return viewWindowGravity;
  }

  public Integer windowWidth() {
    return windowWidth;
  }

  public Integer windowHeight() {
    return windowHeight;
  }

  public Boolean backgroundEnabled() {
    return backgroundEnabled;
  }

  public Boolean animate() {
    return animate;
  }

  public Boolean autoDismiss() {
    return autoDismiss;
  }

  public int getMaxImageHeight() {
    return (int) (maxImageHeightWeight() * maxDialogHeightPx());
  }

  public int getMaxImageWidth() {
    return (int) (maxImageWidthWeight() * maxDialogWidthPx());
  }

  public int getMaxBodyHeight() {
    return (int) (maxBodyHeightWeight() * maxDialogHeightPx());
  }

  public int getMaxBodyWidth() {
    return (int) (maxBodyWidthWeight() * maxDialogWidthPx());
  }

  public static class Builder {

    private final InAppMessageLayoutConfig config;

    public Builder() {
      config = new InAppMessageLayoutConfig();
    }

    public Builder setMaxImageHeightWeight(Float maxImageHeightWeight) {
      config.maxImageHeightWeight = maxImageHeightWeight;
      return this;
    }

    public Builder setMaxImageWidthWeight(Float maxImageWidthWeight) {
      config.maxImageWidthWeight = maxImageWidthWeight;
      return this;
    }

    public Builder setMaxBodyHeightWeight(Float maxBodyHeightWeight) {
      config.maxBodyHeightWeight = maxBodyHeightWeight;
      return this;
    }

    public Builder setMaxBodyWidthWeight(Float maxBodyWidthWeight) {
      config.maxBodyWidthWeight = maxBodyWidthWeight;
      return this;
    }

    public Builder setMaxDialogHeightPx(Integer maxDialogHeightPx) {
      config.maxDialogHeightPx = maxDialogHeightPx;
      return this;
    }

    public Builder setMaxDialogWidthPx(Integer maxDialogWidthPx) {
      config.maxDialogWidthPx = maxDialogWidthPx;
      return this;
    }

    public Builder setViewWindowGravity(Integer viewWindowGravity) {
      config.viewWindowGravity = viewWindowGravity;
      return this;
    }

    public Builder setWindowFlag(Integer windowFlag) {
      config.windowFlag = windowFlag;
      return this;
    }

    public Builder setWindowWidth(Integer windowWidth) {
      config.windowWidth = windowWidth;
      return this;
    }

    public Builder setWindowHeight(Integer windowHeight) {
      config.windowHeight = windowHeight;
      return this;
    }

    public Builder setBackgroundEnabled(Boolean backgroundEnabled) {
      config.backgroundEnabled = backgroundEnabled;
      return this;
    }

    public Builder setAnimate(Boolean animate) {
      config.animate = animate;
      return this;
    }

    public Builder setAutoDismiss(Boolean autoDismiss) {
      config.autoDismiss = autoDismiss;
      return this;
    }

    public InAppMessageLayoutConfig build() {
      return config;
    }
  }
}
