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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.model.GlideUrl;
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
  @Mock private RequestManager glideRequestManager;
  private FiamImageLoader imageLoader;
  @Mock private RequestBuilder<Drawable> requestBuilder;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    imageLoader = new FiamImageLoader(glideRequestManager);
    when(glideRequestManager.load(any(GlideUrl.class))).thenReturn(requestBuilder);
    when(requestBuilder.format(any())).thenReturn(requestBuilder);
  }

  @Test
  public void testLoad_ReturnsFiamImageRequestCreator() {
    assertThat(imageLoader.load(IMAGE_URL).getClass())
        .isEqualTo(FiamImageLoader.FiamImageRequestCreator.class);
  }

  @Test
  public void placeholder_setsPlaceholderOnUnderlyingRequestCreator() {
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    fiamImageRequestCreator.placeholder(1);
    verify(requestBuilder).placeholder(1);
  }

  @Test
  public void addErrorListener_setsErrorListenerOnUnderlyingRequestCreator() {
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    GlideErrorListener errorListener = new GlideErrorListener(null, null);
    fiamImageRequestCreator.addErrorListener(errorListener);
    verify(requestBuilder).addListener(errorListener);
  }

  @Test
  public void tag_tagsUnderlyingRequestCreator() {
    ImageView imageView = mock(ImageView.class);
    FiamImageLoader.Callback callback = mock(FiamImageLoader.Callback.class);
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    fiamImageRequestCreator.into(imageView, callback);
    assertFalse(imageLoader.containsTag(String.class.getSimpleName()));
    fiamImageRequestCreator.tag(String.class);
    assertTrue(imageLoader.containsTag(String.class.getSimpleName()));
  }

  @Test
  public void into_invokesUnderlyingRequestCreator() {
    ImageView imageView = mock(ImageView.class);
    FiamImageLoader.Callback callback = mock(FiamImageLoader.Callback.class);
    FiamImageLoader.FiamImageRequestCreator fiamImageRequestCreator = imageLoader.load(IMAGE_URL);
    fiamImageRequestCreator.into(imageView, callback);
    verify(requestBuilder).into(callback);
  }
}
