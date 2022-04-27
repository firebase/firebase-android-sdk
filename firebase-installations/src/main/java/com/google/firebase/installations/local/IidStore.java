// Copyright 2019 Google LLC
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

package com.google.firebase.installations.local;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Read existing iid only for default (first initialized) instance of this firebase application.*
 *
 * @hide
 */
public class IidStore {
  private static final String IID_SHARED_PREFS_NAME = "com.google.android.gms.appid";
  private static final String STORE_KEY_PUB = "|S||P|";
  private static final String STORE_KEY_ID = "|S|id";
  private static final String STORE_KEY_TOKEN = "|T|";
  private static final String STORE_KEY_SEPARATOR = "|";
  private static final String JSON_TOKEN_KEY = "token";
  private static final String JSON_ENCODED_PREFIX = "{";
  private static final String[] ALLOWABLE_SCOPES = new String[] {"*", "FCM", "GCM", ""};

  @GuardedBy("iidPrefs")
  private final SharedPreferences iidPrefs;

  private final String defaultSenderId;

  public IidStore(@NonNull FirebaseApp firebaseApp) {
    iidPrefs =
        firebaseApp
            .getApplicationContext()
            .getSharedPreferences(IID_SHARED_PREFS_NAME, Context.MODE_PRIVATE);

    defaultSenderId = getDefaultSenderId(firebaseApp);
  }

  @VisibleForTesting
  public IidStore(@NonNull SharedPreferences iidPrefs, @Nullable String defaultSenderId) {
    this.iidPrefs = iidPrefs;
    this.defaultSenderId = defaultSenderId;
  }

  private static String getDefaultSenderId(FirebaseApp app) {
    // Check for an explicit sender id
    String senderId = app.getOptions().getGcmSenderId();
    if (senderId != null) {
      return senderId;
    }
    String appId = app.getOptions().getApplicationId();
    if (!appId.startsWith("1:") && !appId.startsWith("2:")) {
      // If applicationId does not contain a (GMP-)App-ID, it contains a Sender identifier
      return appId;
    }
    // For v1 app IDs, fall back to parsing the project number out
    @SuppressWarnings("StringSplitter")
    String[] parts = appId.split(":");
    if (parts.length != 4) {
      return null; // Invalid format
    }
    String projectNumber = parts[1];
    if (projectNumber.isEmpty()) {
      return null; // No project number
    }
    return projectNumber;
  }

  private String createTokenKey(@NonNull String senderId, @NonNull String scope) {
    return STORE_KEY_TOKEN + senderId + STORE_KEY_SEPARATOR + scope;
  }

  @Nullable
  public String readToken() {
    synchronized (iidPrefs) {
      for (String scope : ALLOWABLE_SCOPES) {
        String tokenKey = createTokenKey(defaultSenderId, scope);
        String token = iidPrefs.getString(tokenKey, null);
        if (token != null && !token.isEmpty()) {
          return token.startsWith(JSON_ENCODED_PREFIX) ? parseIidTokenFromJson(token) : token;
        }
      }

      return null;
    }
  }

  private String parseIidTokenFromJson(String token) {
    // Encoded as JSON
    try {
      JSONObject json = new JSONObject(token);
      return json.getString(JSON_TOKEN_KEY);
    } catch (JSONException e) {
      return null;
    }
  }

  @Nullable
  public String readIid() {
    synchronized (iidPrefs) {
      // Background: Some versions of the IID-SDK store the Instance-ID in local storage,
      // others only store the App-Instance's Public-Key that can be used to calculate the
      // Instance-ID.

      // If such a version was used by this App-Instance, we can directly read the existing
      // Instance-ID from storage and return it
      String id = readInstanceIdFromLocalStorage();

      if (id != null) {
        return id;
      }

      // If this App-Instance did not store the Instance-ID in local storage, we may be able to find
      // its Public-Key in order to calculate the App-Instance's Instance-ID.
      return readPublicKeyFromLocalStorageAndCalculateInstanceId();
    }
  }

  @Nullable
  private String readInstanceIdFromLocalStorage() {
    synchronized (iidPrefs) {
      return iidPrefs.getString(STORE_KEY_ID, /* defaultValue= */ null);
    }
  }

  @Nullable
  private String readPublicKeyFromLocalStorageAndCalculateInstanceId() {
    synchronized (iidPrefs) {
      String base64PublicKey = iidPrefs.getString(STORE_KEY_PUB, /* defaultValue= */ null);
      if (base64PublicKey == null) {
        return null;
      }

      PublicKey publicKey = parseKey(base64PublicKey);
      if (publicKey == null) {
        return null;
      }

      return getIdFromPublicKey(publicKey);
    }
  }

  @Nullable
  private static String getIdFromPublicKey(@NonNull PublicKey publicKey) {
    // The ID is the sha of the public key truncated to 60 bit, with first 4 bits switched to
    // 0x9 and base64 encoded
    // This allows the id to be used internally for legacy systems and differentiate from
    // old android ids and gcm ids

    byte[] derPub = publicKey.getEncoded();
    try {
      // FirebaseInstallations SDK uses the SHA1 hash for backwards compatibility with the legacy
      // InstanceID SDK. The SHA1 hash is used to access Instance IDs stored on the device and not
      // for any security relevant process. This is a one-time step that allows migration of old
      // client identifiers. Cryptographic security is not needed here so potential hash collisions
      // are not a problem.
      MessageDigest md = MessageDigest.getInstance("SHA1");

      byte[] digest = md.digest(derPub);
      int b0 = digest[0];
      b0 = 0x70 + (0xF & b0);
      digest[0] = (byte) (b0 & 0xFF);
      return Base64.encodeToString(
          digest, 0, 8, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    } catch (NoSuchAlgorithmException e) {
      Log.w(TAG, "Unexpected error, device missing required algorithms");
    }
    return null;
  }

  /** Parse the public key from stored data. */
  @Nullable
  private PublicKey parseKey(String base64PublicKey) {
    byte[] publicKeyBytes;
    try {
      publicKeyBytes = Base64.decode(base64PublicKey, Base64.URL_SAFE);
      KeyFactory kf = KeyFactory.getInstance("RSA");
      return kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
      Log.w(TAG, "Invalid key stored " + e);
    }
    return null;
  }
}
