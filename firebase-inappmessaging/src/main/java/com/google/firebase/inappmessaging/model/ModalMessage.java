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

/** Encapsulates a Firebase In App Modal Message. */
public class ModalMessage extends InAppMessage {
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
    int actionHash = action != null ? action.hashCode() : 0;
    int imageHash = imageData != null ? imageData.hashCode() : 0;
    return title.hashCode() + bodyHash + backgroundHexColor.hashCode() + actionHash + imageHash;
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof ModalMessage)) {
      return false; // not the correct instance type
    }
    ModalMessage m = (ModalMessage) o;
    if (hashCode() != m.hashCode()) {
      return false; // the hashcodes don't match
    }
    if ((body == null && m.body != null) || (body != null && !body.equals(m.body))) {
      return false; // the bodies don't match
    }
    if ((action == null && m.action != null) || (action != null && !action.equals(m.action))) {
      return false; // the actions don't match
    }
    if ((imageData == null && m.imageData != null)
        || (imageData != null && !imageData.equals(m.imageData))) {
      return false; // the image data don't match
    }
    if (!title.equals(m.title)) {
      return false; // the titles don't match
    }
    if (backgroundHexColor.equals(m.backgroundHexColor)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private ModalMessage(
      @NonNull CampaignMetadata campaignMetadata,
      @NonNull Text title,
      @Nullable Text body,
      @Nullable ImageData imageData,
      @Nullable Action action,
      @NonNull String backgroundHexColor,
      @Nullable Map<String, String> data) {
    super(campaignMetadata, MessageType.MODAL, data);
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

  /** Gets the background hex color associated with this message */
  @Override
  @NonNull
  public String getBackgroundHexColor() {
    return backgroundHexColor;
  }

  /** Gets the {@link Action} associated with this message */
  @Override
  @Nullable
  public Action getAction() {
    return action;
  }

  /**
   * only used by headless sdk and tests
   *
   * @hide
   */
  public static Builder builder() {
    return new ModalMessage.Builder();
  }

  /**
   * Builder for {@link ModalMessage}
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

    public ModalMessage build(
        CampaignMetadata campaignMetadata, @Nullable Map<String, String> data) {
      if (title == null) {
        throw new IllegalArgumentException("Modal model must have a title");
      }
      if (action != null && action.getButton() == null) {
        throw new IllegalArgumentException("Modal model action must be null or have a button");
      }
      if (TextUtils.isEmpty(backgroundHexColor)) {
        throw new IllegalArgumentException("Modal model must have a background color");
      }

      // We know backgroundColor is not null here because isEmpty checks for null.
      return new ModalMessage(
          campaignMetadata, title, body, imageData, action, backgroundHexColor, data);
    }
  }
}
