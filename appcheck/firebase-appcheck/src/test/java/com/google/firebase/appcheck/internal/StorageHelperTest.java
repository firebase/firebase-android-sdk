// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.appcheck.AppCheckToken;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link StorageHelper} */
@RunWith(RobolectricTestRunner.class)
public class StorageHelperTest {

  private static final String PERSISTENCE_KEY = "persistenceKey";
  private static final String RAW_TOKEN = "rawToken";
  private static final String UNKNOWN_TOKEN_TYPE = "unknownTokenType";
  private static final long EXPIRES_IN = 60L;
  private static final long RECEIVED_AT_TIMESTAMP = 10L;
  private static final long IAT = 10L;
  private static final long EXP = 30L;
  private static final long ONE_SECOND_MILLIS = 1000L;
  private static final String TOKEN_PREFIX = "prefix";
  private static final String TOKEN_SUFFIX = "suffix";
  private static final String SEPARATOR = ".";

  private StorageHelper storageHelper;
  private SharedPreferences sharedPreferences;

  @Before
  public void setUp() {
    storageHelper = new StorageHelper(ApplicationProvider.getApplicationContext(), PERSISTENCE_KEY);
    sharedPreferences =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences(
                String.format(StorageHelper.PREFS_TEMPLATE, PERSISTENCE_KEY), Context.MODE_PRIVATE);
  }

  @After
  public void tearDown() {
    sharedPreferences.edit().clear().commit();
  }

  @Test
  public void testSaveToken_DefaultAppCheckToken_sharedPrefsWritten() {
    DefaultAppCheckToken defaultAppCheckToken =
        new DefaultAppCheckToken(RAW_TOKEN, EXPIRES_IN, RECEIVED_AT_TIMESTAMP);
    storageHelper.saveAppCheckToken(defaultAppCheckToken);
    String token = sharedPreferences.getString(StorageHelper.TOKEN_KEY, null);
    assertThat(token).isNotEmpty();
    assertThat(token).isEqualTo(defaultAppCheckToken.serializeTokenToString());
    String tokenType = sharedPreferences.getString(StorageHelper.TOKEN_TYPE_KEY, null);
    assertThat(tokenType).isNotEmpty();
    assertThat(tokenType).isEqualTo(StorageHelper.TokenType.DEFAULT_APP_CHECK_TOKEN.name());
  }

  @Test
  public void testSaveToken_TestAppCheckToken_sharedPrefsWritten() {
    TestAppCheckToken testAppCheckToken = new TestAppCheckToken(RAW_TOKEN);
    storageHelper.saveAppCheckToken(testAppCheckToken);
    String token = sharedPreferences.getString(StorageHelper.TOKEN_KEY, null);
    assertThat(token).isNotEmpty();
    assertThat(token).isEqualTo(RAW_TOKEN);
    String tokenType = sharedPreferences.getString(StorageHelper.TOKEN_TYPE_KEY, null);
    assertThat(tokenType).isNotEmpty();
    assertThat(tokenType).isEqualTo(StorageHelper.TokenType.UNKNOWN_APP_CHECK_TOKEN.name());
  }

  @Test
  public void testSaveAndRetrieveToken_DefaultAppCheckToken_expectEquivalentToken() {
    DefaultAppCheckToken defaultAppCheckToken =
        new DefaultAppCheckToken(RAW_TOKEN, EXPIRES_IN, RECEIVED_AT_TIMESTAMP);
    storageHelper.saveAppCheckToken(defaultAppCheckToken);
    DefaultAppCheckToken retrievedToken =
        (DefaultAppCheckToken) storageHelper.retrieveAppCheckToken();
    assertThat(retrievedToken).isNotNull();
    assertThat(retrievedToken.getToken()).isEqualTo(RAW_TOKEN);
    assertThat(retrievedToken.getExpiresInMillis()).isEqualTo(EXPIRES_IN);
    assertThat(retrievedToken.getReceivedAtTimestamp()).isEqualTo(RECEIVED_AT_TIMESTAMP);
  }

  @Test
  public void testSaveAndRetrieveToken_TestAppCheckToken_expectEquivalentToken() throws Exception {
    String rawToken = constructFakeRawToken();
    TestAppCheckToken testAppCheckToken = new TestAppCheckToken(rawToken);
    storageHelper.saveAppCheckToken(testAppCheckToken);
    DefaultAppCheckToken retrievedToken =
        (DefaultAppCheckToken) storageHelper.retrieveAppCheckToken();
    assertThat(retrievedToken).isNotNull();
    assertThat(retrievedToken.getToken()).isEqualTo(rawToken);
    assertThat(retrievedToken.getExpiresInMillis()).isEqualTo((EXP - IAT) * ONE_SECOND_MILLIS);
    assertThat(retrievedToken.getReceivedAtTimestamp()).isEqualTo(IAT * ONE_SECOND_MILLIS);
  }

  @Test
  public void testRetrieveToken_unknownTokenTypeSaved_expectReturnsNull() {
    sharedPreferences
        .edit()
        .putString(StorageHelper.TOKEN_KEY, RAW_TOKEN)
        .putString(StorageHelper.TOKEN_TYPE_KEY, UNKNOWN_TOKEN_TYPE)
        .commit();
    AppCheckToken token = storageHelper.retrieveAppCheckToken();
    assertThat(token).isNull();
  }

  private String constructFakeRawToken() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(DefaultAppCheckToken.ISSUED_AT_KEY, IAT);
    jsonObject.put(DefaultAppCheckToken.EXPIRATION_TIME_KEY, EXP);
    String tokenValue = jsonObject.toString();
    // Raw tokens are JWTs with 3 parts which are split by '.'; we attach a prefix and suffix so
    // it can be parsed properly
    return TOKEN_PREFIX
        + SEPARATOR
        + Base64.encodeToString(
            tokenValue.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)
        + SEPARATOR
        + TOKEN_SUFFIX;
  }

  private static class TestAppCheckToken extends AppCheckToken {

    private String token;

    TestAppCheckToken(String rawToken) {
      this.token = rawToken;
    }

    @NonNull
    @Override
    public String getToken() {
      return token;
    }

    @Override
    public long getExpireTimeMillis() {
      return 0L;
    }
  }
}
