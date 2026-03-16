/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.api;

import static org.junit.Assert.assertEquals;

import com.google.firebase.crashlytics.buildtools.api.net.proxy.ProtocolScheme;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProtocolSchemeTest {
  private final String _url;
  private final ProtocolScheme _expected;

  public ProtocolSchemeTest(String url, ProtocolScheme expected) {
    this._url = url;
    this._expected = expected;
  }

  @Parameterized.Parameters(name = "{index}: URL[{0}], Expected Protocol[{1}]")
  public static Iterable<Object[]> testData() {
    return Arrays.asList(
        new Object[][] {
          {WebApi.DEFAULT_CODEMAPPING_API_URL, ProtocolScheme.HTTPS},
          {"https://www.google.com", ProtocolScheme.HTTPS},
          {"http://www.google.com", ProtocolScheme.HTTP},
          {"ftp://www.google.com", ProtocolScheme.Other}
        });
  }

  @Test
  public void testGetType() throws MalformedURLException, URISyntaxException {
    URL url = new URL(this._url);
    assertEquals(_expected, ProtocolScheme.getType(url));

    HttpGet get = new HttpGet(url.toURI());
    assertEquals(_expected, ProtocolScheme.getType(get.getURI()));
  }
}
