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
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultUserAgentPublisherTest {
  private Set<LibraryVersion> libraryVersions;
  private DefaultUserAgentPublisher userAgentPublisher;
  private GlobalLibraryVersionRegistrar globalLibraryVersionRegistrar;

  @Before
  public void before() {
    libraryVersions = new HashSet<>();
    libraryVersions.add(LibraryVersion.create("foo", "1"));
    libraryVersions.add(LibraryVersion.create("bar", "2"));

    globalLibraryVersionRegistrar = mock(GlobalLibraryVersionRegistrar.class);

    when(globalLibraryVersionRegistrar.getRegisteredVersions()).thenReturn(new HashSet<>());

    userAgentPublisher =
        new DefaultUserAgentPublisher(libraryVersions, globalLibraryVersionRegistrar);
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
    userAgentPublisher =
        new DefaultUserAgentPublisher(new HashSet<>(), globalLibraryVersionRegistrar);

    assertThat(userAgentPublisher.getUserAgent()).isEqualTo("");
  }

  @Test
  public void
      getUserAgent_returnsStringIncludingGamesSDKVersions_whenGamesSDKVersionRegistrarReturnsVersions() {
    String[] expectedUserAgent = {"bar/2", "buzz/2", "fizz/1", "foo/1"};
    HashSet<LibraryVersion> gamesLibraryVersions = new HashSet<>();
    gamesLibraryVersions.add(LibraryVersion.create("fizz", "1"));
    gamesLibraryVersions.add(LibraryVersion.create("buzz", "2"));
    when(globalLibraryVersionRegistrar.getRegisteredVersions()).thenReturn(gamesLibraryVersions);
    userAgentPublisher =
        new DefaultUserAgentPublisher(libraryVersions, globalLibraryVersionRegistrar);

    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");
    Arrays.sort(actualUserAgent);

    assertThat(actualUserAgent).isEqualTo(expectedUserAgent);
  }
}
