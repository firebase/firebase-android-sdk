// Copyright 2020 Google LLC
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

package com.google.firebase.perf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.util.URLAllowlist.isURLAllowlisted;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import java.net.URI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link URLAllowlist}. */
@RunWith(RobolectricTestRunner.class)
public class URLAllowlistTest {

  @Test
  public void hostLevelDomainsWithoutConfigFile_returnsTrue() throws Exception {
    Context context = createDummyContext();

    assertThat(isURLAllowlisted(URI.create("http://google.com"), context)).isTrue();
    assertThat(isURLAllowlisted(URI.create("http://gooogle.com"), context)).isTrue();
  }

  @Test
  public void invalidHostButValidUri_returnsTrue() {
    assertThat(
            isURLAllowlisted(
                URI.create("validUriInvalidHostName"), ApplicationProvider.getApplicationContext()))
        .isTrue();
  }

  @Test
  public void subdomainsWithoutConfigFile_returnTrue() throws Exception {
    Context context = createDummyContext();

    assertThat(isURLAllowlisted(URI.create("http://google.com"), context)).isTrue();
    assertThat(isURLAllowlisted(URI.create("http://mail.google.com"), context)).isTrue();
    assertThat(isURLAllowlisted(URI.create("http://super.mail.google.com"), context)).isTrue();
  }

  @Test
  public void wwwDomainsWithoutConfigFile_returnTrue() throws Exception {
    Context context = createDummyContext();

    assertThat(isURLAllowlisted(URI.create("http://www.google.com"), context)).isTrue();
    assertThat(isURLAllowlisted(URI.create("http://www.mail.google.com"), context)).isTrue();
    assertThat(isURLAllowlisted(URI.create("http://www.super.mail.google.com"), context)).isTrue();

    assertThat(isURLAllowlisted(URI.create("http://www.gooogle.com"), context)).isTrue();
    assertThat(isURLAllowlisted(URI.create("http://www.mail.gooogle.com"), context)).isTrue();
  }

  @Test
  public void hostLevelDomainsWithConfigFile_respectTheAllowlist() {
    assertThat(
            isURLAllowlisted(
                URI.create("http://google.com"), ApplicationProvider.getApplicationContext()))
        .isTrue();
    assertThat(
            isURLAllowlisted(
                URI.create("http://gooogle.com"), ApplicationProvider.getApplicationContext()))
        .isFalse();
  }

  @Test
  public void subdomainsWithConfigFile_respectTheAllowlist() {
    assertThat(
            isURLAllowlisted(
                URI.create("http://google.com"), ApplicationProvider.getApplicationContext()))
        .isTrue();
    assertThat(
            isURLAllowlisted(
                URI.create("http://mail.google.com"), ApplicationProvider.getApplicationContext()))
        .isTrue();
    assertThat(
            isURLAllowlisted(
                URI.create("http://super.mail.google.com"),
                ApplicationProvider.getApplicationContext()))
        .isTrue();
  }

  @Test
  public void wwwDomainsWithConfigFile_respectTheAllowlist() {
    assertThat(
            isURLAllowlisted(
                URI.create("http://www.google.com"), ApplicationProvider.getApplicationContext()))
        .isTrue();
    assertThat(
            isURLAllowlisted(
                URI.create("http://www.mail.google.com"),
                ApplicationProvider.getApplicationContext()))
        .isTrue();
    assertThat(
            isURLAllowlisted(
                URI.create("http://www.super.mail.google.com"),
                ApplicationProvider.getApplicationContext()))
        .isTrue();

    assertThat(
            isURLAllowlisted(
                URI.create("http://www.gooogle.com"), ApplicationProvider.getApplicationContext()))
        .isFalse();
    assertThat(
            isURLAllowlisted(
                URI.create("http://www.mail.gooogle.com"),
                ApplicationProvider.getApplicationContext()))
        .isFalse();
  }

  /** Return a {@link Context} with a dummy name so that resource resolution will fail. */
  private static Context createDummyContext() throws PackageManager.NameNotFoundException {
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.packageName = "com.bogus";
    shadowOf(ApplicationProvider.getApplicationContext().getPackageManager())
        .addPackage(packageInfo);
    return ApplicationProvider.getApplicationContext().createPackageContext("com.bogus", 0);
  }
}
