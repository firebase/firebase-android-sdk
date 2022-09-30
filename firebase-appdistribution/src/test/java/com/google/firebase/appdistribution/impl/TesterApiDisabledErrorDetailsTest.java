// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.appdistribution.impl.TesterApiDisabledErrorDetails.HelpLink;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TesterApiDisabledErrorDetailsTest {

  @Test
  public void tryParse_success() throws IOException {
    String responseBody = TestUtils.readTestFile("apiDisabledResponse.json");

    TesterApiDisabledErrorDetails details = TesterApiDisabledErrorDetails.tryParse(responseBody);

    assertThat(details.helpLinks())
        .containsExactly(
            HelpLink.create(
                "Google developers console API activation",
                "https://console.developers.google.com/apis/api/firebaseapptesters.googleapis.com/overview?project=123456789"));
  }

  @Test
  public void tryParse_badResponseBody_returnsNull() {
    String responseBody = "not json";

    TesterApiDisabledErrorDetails details = TesterApiDisabledErrorDetails.tryParse(responseBody);

    assertThat(details).isNull();
  }

  @Test
  public void tryParse_errorParsingLinks_stillReturnsDetails() throws IOException {
    String responseBody = TestUtils.readTestFile("apiDisabledBadLinkResponse.json");

    TesterApiDisabledErrorDetails details = TesterApiDisabledErrorDetails.tryParse(responseBody);

    assertThat(details.helpLinks())
        .containsExactly(
            HelpLink.create("One link", "http://google.com"),
            HelpLink.create("Another link", "http://gmail.com"));
  }

  @Test
  public void formatLinks_success() {
    List<HelpLink> helpLinks = new ArrayList<>();
    helpLinks.add(HelpLink.create("One link", "http://google.com"));
    helpLinks.add(HelpLink.create("Another link", "http://gmail.com"));
    TesterApiDisabledErrorDetails details = new AutoValue_TesterApiDisabledErrorDetails(helpLinks);

    String formattedLinks = details.formatLinks();

    assertThat(formattedLinks)
        .isEqualTo("One link: http://google.com\nAnother link: http://gmail.com\n");
  }
}
