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

package com.example.firebase.fiamui;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

public class TestConstants {

  // Title text IDs
  @StringRes public static final int TITLE_TEXT_NONE = -1;
  @StringRes public static final int TITLE_TEXT_NORMAL = R.string.default_message_title;

  // Body text IDs
  @StringRes public static final int BODY_TEXT_NORMAL = R.string.body_text_normal;
  @StringRes public static final int BODY_TEXT_LONG = R.string.body_text_long;

  // Button text IDs
  @StringRes public static final int BUTTON_TEXT_NORMAL = R.string.view_wishlist;
  @StringRes public static final int BUTTON_TEXT_NONE = -1;

  // Body radio button IDs
  @IdRes public static final int BODY_OPT_NORMAL = R.id.normal_body_text;
  @IdRes public static final int BODY_OPT_LONG = R.id.long_body_text;
  @IdRes public static final int BODY_OPT_NONE = R.id.no_body_text;
}
