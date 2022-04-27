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
import androidx.annotation.Nullable;
import java.util.Map;

/** Encapsulates a Firebase In App Card Message. */
public class CardMessage extends InAppMessage {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @NonNull private final Text title;

  @Nullable private final Text body;
  @NonNull private final String backgroundHexColor;
  @NonNull private final Action primaryAction;
  @Nullable private final Action secondaryAction;
  @Nullable private final ImageData portraitImageData;
  @Nullable private final ImageData landscapeImageData;

  /** @hide */
  @Override
  public int hashCode() {
    int bodyHash = body != null ? body.hashCode() : 0;
    int secondaryActionHash = secondaryAction != null ? secondaryAction.hashCode() : 0;
    int portraitImageHash = portraitImageData != null ? portraitImageData.hashCode() : 0;
    int landscapeImageHash = landscapeImageData != null ? landscapeImageData.hashCode() : 0;
    return title.hashCode()
        + bodyHash
        + backgroundHexColor.hashCode()
        + primaryAction.hashCode()
        + secondaryActionHash
        + portraitImageHash
        + landscapeImageHash;
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof CardMessage)) {
      return false; // not the correct instance type
    }
    CardMessage c = (CardMessage) o;
    if (hashCode() != c.hashCode()) {
      return false; // the hashcodes don't match
    }
    if ((body == null && c.body != null) || (body != null && !body.equals(c.body))) {
      return false; // the bodies don't match
    }
    if ((secondaryAction == null && c.secondaryAction != null)
        || (secondaryAction != null && !secondaryAction.equals(c.secondaryAction))) {
      return false; // the secondary actions don't match
    }
    if ((portraitImageData == null && c.portraitImageData != null)
        || (portraitImageData != null && !portraitImageData.equals(c.portraitImageData))) {
      return false; // the portrait image data don't match
    }
    if ((landscapeImageData == null && c.landscapeImageData != null)
        || (landscapeImageData != null && !landscapeImageData.equals(c.landscapeImageData))) {
      return false; // the landscape image data don't match
    }
    if (!title.equals(c.title)) {
      return false; // the titles don't match
    }
    if (!primaryAction.equals(c.primaryAction)) {
      return false; // the primary actions don't match
    }
    if (backgroundHexColor.equals(c.backgroundHexColor)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private CardMessage(
      @NonNull CampaignMetadata campaignMetadata,
      @NonNull Text title,
      @Nullable Text body,
      @Nullable ImageData portraitImageData,
      @Nullable ImageData landscapeImageData,
      @NonNull String backgroundHexColor,
      @NonNull Action primaryAction,
      @Nullable Action secondaryAction,
      @Nullable Map<String, String> data) {
    super(campaignMetadata, MessageType.CARD, data);
    this.title = title;
    this.body = body;
    this.portraitImageData = portraitImageData;
    this.landscapeImageData = landscapeImageData;
    this.backgroundHexColor = backgroundHexColor;
    this.primaryAction = primaryAction;
    this.secondaryAction = secondaryAction;
  }

  /** Gets the {@link ImageData} displayed when the phone is in a portrait orientation */
  @Nullable
  public ImageData getPortraitImageData() {
    return portraitImageData;
  }

  /** Gets the {@link ImageData} displayed when the phone is in a landscape orientation */
  @Nullable
  public ImageData getLandscapeImageData() {
    return landscapeImageData;
  }

  /** Gets the background hex color associated with this message */
  @Override
  @NonNull
  public String getBackgroundHexColor() {
    return backgroundHexColor;
  }

  /**
   * Gets the primary {@link Action} associated with this message. If none is defined, the primary
   * action is 'dismiss'
   */
  @NonNull
  public Action getPrimaryAction() {
    return primaryAction;
  }

  /** Gets the secondary {@link Action} associated with this message */
  @Nullable
  public Action getSecondaryAction() {
    return secondaryAction;
  }

  /** @deprecated Use {@link #getPrimaryAction()} or {@link #getSecondaryAction()} instead. */
  @Nullable
  @Deprecated
  @Override
  public Action getAction() {
    return primaryAction;
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

  /**
   * @deprecated Use {@link #getPortraitImageData()} or {@link #getLandscapeImageData()} instead.
   */
  @Nullable
  @Deprecated
  @Override
  public ImageData getImageData() {
    return portraitImageData;
  }

  /**
   * only used by headless sdk and tests
   *
   * @hide
   */
  public static Builder builder() {
    return new CardMessage.Builder();
  }

  /**
   * Builder for {@link CardMessage}
   *
   * @hide
   */
  public static class Builder {
    @Nullable ImageData portraitImageData;
    @Nullable ImageData landscapeImageData;
    @Nullable String backgroundHexColor;
    @Nullable Action primaryAction;
    @Nullable Text title;
    @Nullable Text body;
    @Nullable Action secondaryAction;

    public Builder setPortraitImageData(@Nullable ImageData portraitImageData) {
      this.portraitImageData = portraitImageData;
      return this;
    }

    public Builder setLandscapeImageData(@Nullable ImageData landscapeImageData) {
      this.landscapeImageData = landscapeImageData;
      return this;
    }

    public Builder setBackgroundHexColor(@Nullable String backgroundHexColor) {
      this.backgroundHexColor = backgroundHexColor;
      return this;
    }

    public Builder setPrimaryAction(@Nullable Action primaryAction) {
      this.primaryAction = primaryAction;
      return this;
    }

    public Builder setSecondaryAction(@Nullable Action secondaryAction) {
      this.secondaryAction = secondaryAction;
      return this;
    }

    public Builder setTitle(@Nullable Text title) {
      this.title = title;
      return this;
    }

    public Builder setBody(@Nullable Text body) {
      this.body = body;
      return this;
    }

    public CardMessage build(
        CampaignMetadata campaignMetadata, @Nullable Map<String, String> data) {
      if (primaryAction == null) {
        throw new IllegalArgumentException("Card model must have a primary action");
      }
      if (primaryAction.getButton() == null) {
        throw new IllegalArgumentException("Card model must have a primary action button");
      }
      if (secondaryAction != null && secondaryAction.getButton() == null) {
        throw new IllegalArgumentException(
            "Card model secondary action must be null or have a button");
      }
      if (title == null) {
        throw new IllegalArgumentException("Card model must have a title");
      }
      if (portraitImageData == null && landscapeImageData == null) {
        throw new IllegalArgumentException("Card model must have at least one image");
      }
      if (TextUtils.isEmpty(backgroundHexColor)) {
        throw new IllegalArgumentException("Card model must have a background color");
      }

      // We know backgroundColor is not null here because isEmpty checks for null.
      return new CardMessage(
          campaignMetadata,
          title,
          body,
          portraitImageData,
          landscapeImageData,
          backgroundHexColor,
          primaryAction,
          secondaryAction,
          data);
    }
  }
}
