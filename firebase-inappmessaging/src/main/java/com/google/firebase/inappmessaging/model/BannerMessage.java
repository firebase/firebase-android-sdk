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

package com.google.firebase.inappmessaging.model;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import java.util.Map;
import javax.annotation.Nullable;

/** Encapsulates a Firebase In App Banner Message. */
public class BannerMessage extends InAppMessage {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @NonNull private final Text title;

  @Nullable private final Text body;
  @Nullable private final ImageData imageData;
  @Nullable private final Action action;
  @NonNull private final String backgroundHexColor;

  /** @hide */
  @Override
  public int hashCode() {
    int bodyHash = body != null ? body.hashCode() : 0;
    int imageHash = imageData != null ? imageData.hashCode() : 0;
    int actionHash = action != null ? action.hashCode() : 0;
    return title.hashCode() + bodyHash + imageHash + actionHash + backgroundHexColor.hashCode();
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof BannerMessage)) {
      return false; // not the correct instance type
    }
    BannerMessage b = (BannerMessage) o;
    if (hashCode() != b.hashCode()) {
      return false; // the hashcodes don't match
    }
    if ((body == null && b.body != null) || (body != null && !body.equals(b.body))) {
      return false; // the bodies don't match
    }
    if ((imageData == null && b.imageData != null)
        || (imageData != null && !imageData.equals(b.imageData))) {
      return false; // the images don't match
    }
    if ((action == null && b.action != null) || (action != null && !action.equals(b.action))) {
      return false; // the actions don't match
    }
    if (!title.equals(b.title)) {
      return false; // the tiles don't match
    }
    if (backgroundHexColor.equals(b.backgroundHexColor)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private BannerMessage(
      @NonNull CampaignMetadata campaignMetadata,
      @NonNull Text title,
      @Nullable Text body,
      @Nullable ImageData imageData,
      @Nullable Action action,
      @NonNull String backgroundHexColor,
      @Nullable Map<String, String> data) {
    super(campaignMetadata, MessageType.BANNER, data);
    this.title = title;
    this.body = body;
    this.imageData = imageData;
    this.action = action;
    this.backgroundHexColor = backgroundHexColor;
  }

  /** Gets the title {@link Text} associated with this message */
  @Override
  @NonNull
  public Text getTitle() {
    return title;
  }

  /** Gets the body {@link Text} associated with this message */
  @Override
  @Nullable
  public Text getBody() {
    return body;
  }

  /** Gets the {@link ImageData} associated with this message */
  @Override
  @Nullable
  public ImageData getImageData() {
    return imageData;
  }

  /** Gets the {@link Action} associated with this message */
  @Override
  @Nullable
  public Action getAction() {
    return action;
  }

  /** Gets the background hex color associated with this message */
  @Override
  @NonNull
  public String getBackgroundHexColor() {
    return backgroundHexColor;
  }

  /**
   * only used by headless sdk and tests
   *
   * @hide
   */
  public static Builder builder() {
    return new BannerMessage.Builder();
  }

  /**
   * Builder for {@link BannerMessage}
   *
   * @hide
   */
  public static class Builder {
    @Nullable Text title;
    @Nullable Text body;
    @Nullable ImageData imageData;
    @Nullable Action action;
    @Nullable String backgroundHexColor;

    public Builder setTitle(@Nullable Text title) {
      this.title = title;
      return this;
    }

    public Builder setBody(@Nullable Text body) {
      this.body = body;
      return this;
    }

    public Builder setImageData(@Nullable ImageData imageData) {
      this.imageData = imageData;
      return this;
    }

    public Builder setAction(@Nullable Action action) {
      this.action = action;
      return this;
    }

    public Builder setBackgroundHexColor(@Nullable String backgroundHexColor) {
      this.backgroundHexColor = backgroundHexColor;
      return this;
    }

    public BannerMessage build(
        CampaignMetadata campaignMetadata, @Nullable Map<String, String> data) {
      if (title == null) {
        throw new IllegalArgumentException("Banner model must have a title");
      }
      if (TextUtils.isEmpty(backgroundHexColor)) {
        throw new IllegalArgumentException("Banner model must have a background color");
      }
      // We know backgroundColor is not null here because isEmpty checks for null.
      return new BannerMessage(
          campaignMetadata, title, body, imageData, action, backgroundHexColor, data);
    }
  }
}
