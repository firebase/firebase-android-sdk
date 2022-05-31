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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ApkHashExtractorTest {

  @Test
  public void extractApkHash_returnsAHash() throws FirebaseAppDistributionException {
    ApkHashExtractor apkHashExtractor =
        new ApkHashExtractor(ApplicationProvider.getApplicationContext());

    assertThat(apkHashExtractor.extractApkHash().matches("^[0-9a-fA-F]+$")).isTrue();
  }

  @Test
  public void extractApkHash_ifKeyInCachedApkHashes_doesNotRecalculateZipHash()
      throws FirebaseAppDistributionException {
    ApkHashExtractor apkHashExtractor =
        Mockito.spy(new ApkHashExtractor(ApplicationProvider.getApplicationContext()));
    apkHashExtractor.extractApkHash();
    apkHashExtractor.extractApkHash();

    // asserts that that calculateApkHash is only called once
    verify(apkHashExtractor).calculateApkHash(any());
  }
}
