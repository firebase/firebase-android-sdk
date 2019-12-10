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

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.net.Uri;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.testing.common.Tasks2;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DynamicLinksTest {

  @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

  @Test
  public void buildDynamicLink_UriContainsCorrectComponents() throws Exception {
    FirebaseDynamicLinks dl = FirebaseDynamicLinks.getInstance();
    Uri uri = Uri.parse("http://www.example.com");

    DynamicLink link =
        dl.createDynamicLink()
            .setLink(uri)
            .setDomainUriPrefix("http://example.page.link")
            .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
            .buildDynamicLink();
    Uri actual = link.getUri();
    String[] query = actual.getQuery().split("&");

    assertThat(actual.getScheme()).isEqualTo("http");
    assertThat(actual.getHost()).isEqualTo("example.page.link");
    assertThat(query)
        .asList()
        .containsAtLeast("apn=com.google.firebase.testing", "link=http://www.example.com");
  }

  @Test
  public void getDynamicLink_NonLinkReturnsNull() throws Exception {
    FirebaseDynamicLinks dl = FirebaseDynamicLinks.getInstance();
    Uri uri = Uri.parse("http://www.example.com");

    Task<?> task = dl.getDynamicLink(uri);
    Object actual = Tasks2.waitForSuccess(task);

    assertThat(actual).isNull();
  }
}
