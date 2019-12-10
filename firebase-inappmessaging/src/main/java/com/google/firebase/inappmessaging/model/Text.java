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
import com.google.firebase.inappmessaging.MessagesProto;

/** Encapsulates any text used in a Firebase In App Message. */
public class Text {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @Nullable private final String text;

  @NonNull private final String hexColor;

  /** @hide */
  @Override
  public int hashCode() {
    return text != null ? text.hashCode() + hexColor.hashCode() : hexColor.hashCode();
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof Text)) {
      return false; // not the correct instance type
    }
    Text t = (Text) o;
    if (hashCode() != t.hashCode()) {
      return false; // the hashcodes don't match=
    }
    if ((text == null && t.text != null) || (text != null && !text.equals(t.text))) {
      return false; // the texts don't match
    }
    if (hexColor.equals(t.hexColor)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private Text(@Nullable String text, @NonNull String hexColor) {
    this.text = text;
    this.hexColor = hexColor;
  }

  /** Gets the text */
  @Nullable
  public String getText() {
    return text;
  }

  /** Gets the hex color of this text */
  @NonNull
  public String getHexColor() {
    return hexColor;
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
   * Builder for {@link Text}
   *
   * @hide
   */
  public static class Builder {
    @Nullable private String text;
    @Nullable private String hexColor;

    public Builder setText(@Nullable String text) {
      this.text = text;
      return this;
    }

    public Builder setText(MessagesProto.Text text) {
      setText(text.getText());
      setHexColor(text.getHexColor());
      return this;
    }

    public Builder setHexColor(@Nullable String hexColor) {
      this.hexColor = hexColor;
      return this;
    }

    public Text build() {
      if (TextUtils.isEmpty(hexColor)) {
        throw new IllegalArgumentException("Text model must have a color");
      }
      // We know hexColor is not null here because isEmpty checks for null.
      return new Text(text, hexColor);
    }
  }
}
