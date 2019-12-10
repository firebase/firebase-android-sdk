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

import androidx.annotation.NonNull;
import java.util.Map;
import javax.annotation.Nullable;

/** Encapsulates a Firebase In App ImageOnly Message. */
public class ImageOnlyMessage extends InAppMessage {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @NonNull private ImageData imageData;

  @Nullable private Action action;

  /** @hide */
  @Override
  public int hashCode() {
    int actionHash = action != null ? action.hashCode() : 0;
    return imageData.hashCode() + actionHash;
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof ImageOnlyMessage)) {
      return false; // not the correct instance type
    }
    ImageOnlyMessage i = (ImageOnlyMessage) o;
    if (hashCode() != i.hashCode()) {
      return false; // the hashcodes don't match
    }
    if ((action == null && i.action != null) || (action != null && !action.equals(i.action))) {
      return false; // the actions don't match
    }
    if (imageData.equals(i.imageData)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private ImageOnlyMessage(
      @NonNull CampaignMetadata campaignMetadata,
      @NonNull ImageData imageData,
      @Nullable Action action,
      @Nullable Map<String, String> data) {
    super(campaignMetadata, MessageType.IMAGE_ONLY, data);
    this.imageData = imageData;
    this.action = action;
  }

  /** Gets the {@link ImageData} associated with this message */
  @Override
  @NonNull
  public ImageData getImageData() {
    return imageData;
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
    return new Builder();
  }

  /**
   * Builder for {@link ImageOnlyMessage}
   *
   * @hide
   */
  public static class Builder {
    @Nullable ImageData imageData;
    @Nullable Action action;

    public Builder setImageData(@Nullable ImageData imageData) {
      this.imageData = imageData;
      return this;
    }

    public Builder setAction(@Nullable Action action) {
      this.action = action;
      return this;
    }

    public ImageOnlyMessage build(
        CampaignMetadata campaignMetadata, @Nullable Map<String, String> data) {
      if (imageData == null) {
        throw new IllegalArgumentException("ImageOnly model must have image data");
      }
      return new ImageOnlyMessage(campaignMetadata, imageData, action, data);
    }
  }
}
