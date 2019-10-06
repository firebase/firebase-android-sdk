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

package com.google.firebase.inappmessaging.display.internal;

import android.app.Activity;
import android.content.Context;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import java.util.Objects;

class TestActivity extends Activity {

  private WindowManager windowManager;

  @Override
  public WindowManager getWindowManager() {
    if (windowManager != null) {
      return windowManager;
    }

    return super.getWindowManager();
  }

  public void setWindowManager(WindowManager windowManager) {
    this.windowManager = windowManager;
  }

  @Override
  public Object getSystemService(@NonNull String name) {
    if (Objects.equals(name, Context.WINDOW_SERVICE) && windowManager != null) {
      return windowManager;
    } else {
      return super.getSystemService(name);
    }
  }
}
