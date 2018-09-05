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

package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;

import com.google.firebase.database.TestValues;
import java.net.URI;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RepoInfoTest {
  @Test
  public void getConnectionURLTestOverloadWorks() {
    RepoInfo info = new RepoInfo();
    info.host = TestValues.TEST_REPO + "." + TestValues.TEST_SERVER;
    info.internalHost = info.host;
    info.secure = false;
    info.namespace = TestValues.TEST_REPO;
    URI url = info.getConnectionURL(null);
    assertEquals(
        String.format(
            "ws://%s.%s/.ws?ns=%s&v=5",
            TestValues.TEST_REPO, TestValues.TEST_SERVER, TestValues.TEST_REPO),
        url.toString());
    url = info.getConnectionURL("test");
    assertEquals(
        String.format(
            "ws://%s.%s/.ws?ns=%s&v=5&ls=test",
            TestValues.TEST_REPO, TestValues.TEST_SERVER, TestValues.TEST_REPO),
        url.toString());
  }
}
