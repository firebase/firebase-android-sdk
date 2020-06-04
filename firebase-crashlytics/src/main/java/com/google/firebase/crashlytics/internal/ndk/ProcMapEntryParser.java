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

package com.google.firebase.crashlytics.internal.ndk;

import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses entries from Linux's "proc/[pid]/maps" listing. */
final class ProcMapEntryParser {

  private ProcMapEntryParser() {
    // Utility class.
  }

  // (Begin Address)-(End address) (Perms) Offset Device Inode (Path)
  private static final Pattern MAP_REGEX =
      Pattern.compile(
          "\\s*(\\p{XDigit}+)-\\s*(\\p{XDigit}+)\\s+(.{4})"
              + "\\s+\\p{XDigit}+\\s+.+\\s+\\d+\\s+(.*)");

  /**
   * Parse a single entry from the Linux "proc/[pid]/maps" listing.
   *
   * @param mapEntry single line entry from from the "/proc/[pid]/maps" listing.
   * @return a ProcMapEntry containing the pertinent data parsed from the string, or null if it
   *     cannot be parsed.
   */
  @Nullable
  static ProcMapEntry parse(String mapEntry) {
    final Matcher m = MAP_REGEX.matcher(mapEntry);

    if (!m.matches()) {
      return null;
    }

    try {
      final long address = Long.valueOf(m.group(1), 16);
      final long size = Long.valueOf(m.group(2), 16) - address;
      final String perms = m.group(3);
      final String path = m.group(4);

      return new ProcMapEntry(address, size, perms, path);
    } catch (Exception e) {
      Logger.getLogger().d("Could not parse map entry: " + mapEntry);
      return null;
    }
  }
}
