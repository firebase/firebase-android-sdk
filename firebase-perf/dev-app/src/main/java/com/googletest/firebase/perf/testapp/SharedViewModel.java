// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googletest.firebase.perf.testapp;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
  public static final String FIREBASE_LOGO_ANIMATION_GIF =
      "https://storage.googleapis.com/stgd/firebase/uST1OoQhwSPLeZ94ZPDAZ63Ayiq1/ce982c30-f114-11e9-9e62-1fe2556ebf85.gif";
  public static final String FIREBASE_SPARKY_PINEAPPLE_GIF =
      "https://media.giphy.com/media/S3QB5UrHpH4Kq5wsax/giphy.gif";
  private final MutableLiveData<String> imageSrc;

  public SharedViewModel() {
    imageSrc = new MutableLiveData<String>();
    imageSrc.setValue(FIREBASE_LOGO_ANIMATION_GIF);
  }

  public void changeImage() {
    imageSrc.setValue(
        imageSrc.getValue().equals(FIREBASE_LOGO_ANIMATION_GIF)
            ? FIREBASE_SPARKY_PINEAPPLE_GIF
            : FIREBASE_LOGO_ANIMATION_GIF);
  }

  public MutableLiveData<String> getImageSrc() {
    return imageSrc;
  }
}
