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

/** Encapsulates any button used in a Firebase In App Message. */
public class Button {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @NonNull private final Text text;

  @NonNull private final String buttonHexColor;

  /** @hide */
  @Override
  public int hashCode() {
    return text.hashCode() + buttonHexColor.hashCode();
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof Button)) {
      return false; // not the correct instance type
    }
    Button b = (Button) o;
    if (hashCode() != b.hashCode()) {
      return false; // the hashcodes don't match
    }
    if (!text.equals(b.text)) {
      return false; // the texts don't match
    }
    if (buttonHexColor.equals(b.buttonHexColor)) {
      return true; // everything matches
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private Button(@NonNull Text text, @NonNull String buttonHexColor) {
    this.text = text;
    this.buttonHexColor = buttonHexColor;
  }

  /** Gets the {@link Text} associated with this button */
  @NonNull
  public Text getText() {
    return text;
  }

  /** Gets the background hex color associated with this button */
  @NonNull
  public String getButtonHexColor() {
    return buttonHexColor;
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
   * Builder for {@link Button}
   *
   * @hide
   */
  public static class Builder {
    @Nullable private Text text;
    @Nullable private String buttonHexColor;

    public Builder setText(@Nullable Text text) {
      this.text = text;
      return this;
    }

    public Builder setText(MessagesProto.Text text) {
      Text.Builder textBuilder = new Text.Builder();
      textBuilder.setText(text);
      this.text = textBuilder.build();
      return this;
    }

    public Builder setButtonHexColor(@Nullable String buttonHexColor) {
      this.buttonHexColor = buttonHexColor;
      return this;
    }

    public Button build() {
      if (TextUtils.isEmpty(buttonHexColor)) {
        throw new IllegalArgumentException("Button model must have a color");
      }
      if (text == null) {
        throw new IllegalArgumentException("Button model must have text");
      }
      // We know buttonColor is not null here because isEmpty checks for null.
      return new Button(text, buttonHexColor);
    }
  }
}
