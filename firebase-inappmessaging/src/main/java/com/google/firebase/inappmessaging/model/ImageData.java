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

import android.graphics.Bitmap;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Encapsulates an image to be displayed within a Firebase In App Message. */
public class ImageData {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @NonNull private final String imageUrl;

  @Nullable private final Bitmap bitmapData;

  /** @hide */
  @Override
  public int hashCode() {
    int bitmapHash = bitmapData != null ? bitmapData.hashCode() : 0;
    return imageUrl.hashCode() + bitmapHash;
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof ImageData)) {
      return false; // not the correct instance type
    }
    ImageData i = (ImageData) o;
    if (hashCode() != i.hashCode()) {
      return false; // the hashcodes don't match
    }
    if (imageUrl.equals(i.imageUrl)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */

  /** @hide */
  public ImageData(@NonNull String imageUrl, @Nullable Bitmap bitmapData) {
    this.imageUrl = imageUrl;
    this.bitmapData = bitmapData;
  }

  /** Gets the URL associated with this image */
  @NonNull
  public String getImageUrl() {
    return imageUrl;
  }

  /** Gets the bitmap associated with this image */
  @Nullable
  public Bitmap getBitmapData() {
    return bitmapData;
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
   * Builder for {@link ImageData}
   *
   * @hide
   */
  public static class Builder {
    @Nullable private String imageUrl;
    @Nullable private Bitmap bitmapData;

    public Builder setImageUrl(@Nullable String imageUrl) {
      if (!TextUtils.isEmpty(imageUrl)) {
        this.imageUrl = imageUrl;
      }
      return this;
    }

    public Builder setBitmapData(@Nullable Bitmap bitmapData) {
      this.bitmapData = bitmapData;
      return this;
    }

    public ImageData build() {
      if (TextUtils.isEmpty(imageUrl)) {
        throw new IllegalArgumentException("ImageData model must have an imageUrl");
      }

      // We know imageUrl is not null here because isEmpty checks for null.
      return new ImageData(imageUrl, bitmapData);
    }
  }
}
