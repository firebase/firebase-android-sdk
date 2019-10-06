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

package com.google.firebase.inappmessaging.display.internal.layout.util;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;

/** @hide */
public class BackButtonHandler {

  private ViewGroup viewGroup;
  private View.OnClickListener listener;

  public BackButtonHandler(ViewGroup viewGroup, View.OnClickListener listener) {
    this.viewGroup = viewGroup;
    this.listener = listener;
  }

  /** Returning "true" or "false" if the event was handled, "null" otherwise. */
  @Nullable
  public Boolean dispatchKeyEvent(KeyEvent event) {
    if (event != null
        && event.getKeyCode() == KeyEvent.KEYCODE_BACK
        && event.getAction() == KeyEvent.ACTION_UP) {

      if (listener != null) {
        listener.onClick(viewGroup);
        return true;
      }

      return false;
    }

    return null;
  }
}
