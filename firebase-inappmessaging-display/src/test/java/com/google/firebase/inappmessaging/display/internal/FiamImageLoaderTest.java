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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.widget.ImageView;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, qualifiers = "por")
public class FiamImageLoaderTest {
  private static final String IMAGE_URL = "https://www.imgur.com";
  @Mock private Picasso picasso;
  private FiamImageLoader imageLoader;
  @Mock private RequestCreator requestCreator;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    imageLoader = new FiamImageLoader(picasso);
  }

  @Test
  public void load_createdFiamImageRequestCreator() {
    when(picasso.load(IMAGE_URL)).thenReturn(requestCreator);

    assertThat(imageLoader.load(IMAGE_URL).getClass())
        .isEqualTo(FiamImageLoader.FiamImageRequestCreator.class);
  }

  @Test
  public void placeholder_setsPlaceholderOnUnderlyingRequestCreator() {
    when(picasso.load(IMAGE_URL)).thenReturn(requestCreator);
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    fiamImageRequestCreator.placeholder(1);

    verify(requestCreator).placeholder(1);
  }

  @Test
  public void tag_tagsUnderlyingRequestCreator() {
    when(picasso.load(IMAGE_URL)).thenReturn(requestCreator);
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    fiamImageRequestCreator.tag(String.class);

    verify(requestCreator).tag(String.class);
  }

  @Test
  public void into_invokesUnderlyingRequestCreator() {
    ImageView imageView = mock(ImageView.class);
    Callback callback = mock(Callback.class);

    when(picasso.load(IMAGE_URL)).thenReturn(requestCreator);
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    fiamImageRequestCreator.into(imageView, callback);

    verify(requestCreator).into(imageView, callback);
  }
}
