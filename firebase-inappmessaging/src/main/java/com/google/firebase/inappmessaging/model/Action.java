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
import androidx.annotation.Nullable;
import com.google.firebase.inappmessaging.MessagesProto;

/** Encapsulates an Action for a Firebase In App Message. */
public class Action {
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  @Nullable private final String actionUrl;

  @Nullable private final Button button;

  /** @hide */
  @Override
  public int hashCode() {
    int urlHash = actionUrl != null ? actionUrl.hashCode() : 0;
    int buttonHash = button != null ? button.hashCode() : 0;
    return urlHash + buttonHash;
  }

  /** @hide */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true; // same instance
    }
    if (!(o instanceof Action)) {
      return false; // not the correct instance type
    }
    Action a = (Action) o;
    if (hashCode() != a.hashCode()) {
      return false; // the hashcodes don't match
    }
    if ((actionUrl == null && a.actionUrl != null)
        || (actionUrl != null && !actionUrl.equals(a.actionUrl))) {
      return false; // the actionUrls don't match
    }
    if ((button == null && a.button == null) || (button != null && button.equals(a.button))) {
      return true; // either both buttons are null, or the two are equal
    }
    return false;
  }
  /**
   * !!!!!WARNING!!!!! We are overriding equality in this class. Please add equality checks for all
   * new private class members.
   */
  private Action(@Nullable String actionUrl, @Nullable Button button) {
    this.actionUrl = actionUrl;
    this.button = button;
  }

  /** Gets the URL associated with this action */
  @Nullable
  public String getActionUrl() {
    return actionUrl;
  }

  /** Gets the {@link Button} associated with this action */
  @Nullable
  public Button getButton() {
    return button;
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
   * Builder for {@link Action}
   *
   * @hide
   */
  public static class Builder {
    @Nullable private String actionUrl;
    @Nullable private Button button;

    public Builder setActionUrl(@Nullable String actionUrl) {
      if (!TextUtils.isEmpty(actionUrl)) {
        this.actionUrl = actionUrl;
      }
      return this;
    }

    public Builder setButton(@Nullable Button button) {
      this.button = button;
      return this;
    }

    public Builder setButton(MessagesProto.Button button) {
      Button.Builder buttonBuilder = new Button.Builder();
      buttonBuilder.setButtonHexColor(button.getButtonHexColor());
      buttonBuilder.setText(button.getText());
      return this;
    }

    // Technically an action can be completely null although in practice only one field at a time
    // is ever null. Unfortunately there is no better way to model this.
    public Action build() {
      return new Action(actionUrl, button);
    }
  }
}
