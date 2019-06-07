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

package com.google.firebase.inappmessaging.display.internal.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import androidx.annotation.RequiresApi;
import com.google.firebase.inappmessaging.display.internal.layout.util.BackButtonHandler;

/** @hide */
public class FiamRelativeLayout extends RelativeLayout implements BackButtonLayout {

  private BackButtonHandler mBackHandler;

  public FiamRelativeLayout(Context context) {
    super(context);
  }

  public FiamRelativeLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public FiamRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @RequiresApi(21)
  public FiamRelativeLayout(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public void setDismissListener(View.OnClickListener listener) {
    mBackHandler = new BackButtonHandler(this, listener);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    Boolean handled = mBackHandler.dispatchKeyEvent(event);
    if (handled != null) {
      return handled;
    } else {
      return super.dispatchKeyEvent(event);
    }
  }
}
