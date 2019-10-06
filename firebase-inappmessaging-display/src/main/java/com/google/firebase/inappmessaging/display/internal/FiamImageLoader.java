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

import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import javax.inject.Inject;

/**
 * Image loader abstraction around the Picasso singleton to facilitate testing and injection
 *
 * @hide
 */
@FirebaseAppScope
public class FiamImageLoader {
  private final Picasso picasso;

  @Inject
  FiamImageLoader(Picasso picasso) {
    this.picasso = picasso;
  }

  public FiamImageRequestCreator load(@Nullable String imageUrl) {
    return new FiamImageRequestCreator(picasso.load(imageUrl));
  }

  public void cancelTag(Class c) {
    picasso.cancelTag(c);
  }

  public static class FiamImageRequestCreator {
    private final RequestCreator mRequestCreator;

    public FiamImageRequestCreator(RequestCreator requestCreator) {
      mRequestCreator = requestCreator;
    }

    public FiamImageRequestCreator placeholder(int placeholderResId) {
      mRequestCreator.placeholder(placeholderResId);
      return this;
    }

    public FiamImageRequestCreator tag(Class c) {
      mRequestCreator.tag(c);
      return this;
    }

    public void into(ImageView imageView, Callback callback) {
      mRequestCreator.into(imageView, callback);
    }
  }
}
