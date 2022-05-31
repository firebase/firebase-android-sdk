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
