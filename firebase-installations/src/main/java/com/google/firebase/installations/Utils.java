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

package com.google.firebase.installations;

import androidx.annotation.NonNull;
import com.google.android.gms.common.util.Clock;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Util methods used for {@link FirebaseInstallations} */
class Utils {

  private final Clock clock;
  /**
   * 1 Byte with the first 4 header-bits set to the identifying FID prefix 0111 (0x7). Use this
   * constant to create FIDs or check the first byte of FIDs. This prefix is also used in legacy
   * Instance-IDs
   */
  public static final byte FID_4BIT_PREFIX = Byte.parseByte("01110000", 2);

  /**
   * Byte mask to remove the 4 header-bits of a given Byte. Use this constant with Java's Binary AND
   * Operator in order to remove the first 4 bits of a Byte and replacing it with the FID prefix.
   */
  public static final byte REMOVE_PREFIX_MASK = Byte.parseByte("00001111", 2);

  /** Length of new-format FIDs as introduced in 2019. */
  public static final int FID_LENGTH = 22;

  private static final long AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS = TimeUnit.HOURS.toSeconds(1);

  Utils(Clock clock) {
    this.clock = clock;
  }

  /**
   * Checks if the FIS Auth token is expired or going to expire in next 1 hour {@link
   * #AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS}.
   */
  public boolean isAuthTokenExpired(PersistedInstallationEntry persistedInstallationEntry) {
    return persistedInstallationEntry.isRegistered()
        && persistedInstallationEntry.getTokenCreationEpochInSecs()
                + persistedInstallationEntry.getExpiresInSecs()
            < currentTimeInSecs() + AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS;
  }

  /** Returns current time in seconds. */
  public long currentTimeInSecs() {
    return TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis());
  }

  /**
   * Creates a random FID of valid format without checking if the FID is already in use by any
   * Firebase Installation.
   *
   * <p>Note: Even though this method does not check with the FIS database if the returned FID is
   * already in use, the probability of collision is extremely and negligibly small!
   *
   * @return random FID value
   */
  @NonNull
  public String createRandomFid() {
    // A valid FID has exactly 22 base64 characters, which is 132 bits, or 16.5 bytes.
    byte[] uuidBytes = getBytesFromUUID(UUID.randomUUID(), new byte[17]);
    uuidBytes[16] = uuidBytes[0];
    uuidBytes[0] = (byte) ((REMOVE_PREFIX_MASK & uuidBytes[0]) | FID_4BIT_PREFIX);
    return encodeFidBase64UrlSafe(uuidBytes);
  }

  /**
   * Converts a given byte-array (assumed to be an FID value) to base64-url-safe encoded
   * String-representation.
   *
   * <p>Note: The returned String has at most 22 characters, the length of FIDs. Thus, it is
   * recommended to deliver a byte-array containing at least 16.5 bytes.
   *
   * @param rawValue FID value to be encoded
   * @return (22-character or shorter) String containing the base64-encoded value
   */
  private static String encodeFidBase64UrlSafe(byte[] rawValue) {
    return new String(
            android.util.Base64.encode(
                rawValue,
                android.util.Base64.URL_SAFE
                    | android.util.Base64.NO_PADDING
                    | android.util.Base64.NO_WRAP),
            Charset.defaultCharset())
        .substring(0, FID_LENGTH);
  }

  private static byte[] getBytesFromUUID(UUID uuid, byte[] output) {
    ByteBuffer bb = ByteBuffer.wrap(output);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }
}
