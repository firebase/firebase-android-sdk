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

package com.google.firebase.platforminfo;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "NoAutoDataCollectionAndroidManifest.xml")
public class DefaultUserAgentPublisherTest {
  Set<SDKVersion> sdkVersions;
  DefaultUserAgentPublisher userAgentPublisher;
  GamesSDKVersionRegistrar gamesSDKVersionRegistrar;

  @Before
  public void before() {
    sdkVersions = new HashSet<>();
    sdkVersions.add(SDKVersion.builder().setSDKName("foo").setVersion("1").build());
    sdkVersions.add(SDKVersion.builder().setSDKName("bar").setVersion("2").build());

    gamesSDKVersionRegistrar = mock(GamesSDKVersionRegistrar.class);

    when(gamesSDKVersionRegistrar.getRegisteredVersions()).thenReturn(new HashSet<>());

    userAgentPublisher = new DefaultUserAgentPublisher(sdkVersions, gamesSDKVersionRegistrar);
  }

  @Test
  public void getUserAgent_createsConcatenatedStringOfSdkVersions() {
    String[] expectedUserAgent = {"bar/2", "foo/1"};

    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");
    Arrays.sort(actualUserAgent);

    assertThat(actualUserAgent).isEqualTo(expectedUserAgent);
  }

  @Test
  public void getUserAgent_returnsEmptyString_whenVersionSetIsEmpty() {
    userAgentPublisher = new DefaultUserAgentPublisher(new HashSet<>(), gamesSDKVersionRegistrar);

    assertThat(userAgentPublisher.getUserAgent()).isEqualTo("");
  }

  @Test
  public void
      getUserAgent_returnsStringIncludingGamesSDKVersions_whenGamesSDKVersionRegistrarReturnsVersions() {
    String[] expectedUserAgent = {"bar/2", "buzz/2", "fizz/1", "foo/1"};
    HashSet<SDKVersion> gamesSDKVersions = new HashSet<>();
    gamesSDKVersions.add(SDKVersion.builder().setSDKName("fizz").setVersion("1").build());
    gamesSDKVersions.add(SDKVersion.builder().setSDKName("buzz").setVersion("2").build());
    when(gamesSDKVersionRegistrar.getRegisteredVersions()).thenReturn(gamesSDKVersions);
    userAgentPublisher = new DefaultUserAgentPublisher(sdkVersions, gamesSDKVersionRegistrar);

    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");
    Arrays.sort(actualUserAgent);

    assertThat(actualUserAgent).isEqualTo(expectedUserAgent);
  }
}
