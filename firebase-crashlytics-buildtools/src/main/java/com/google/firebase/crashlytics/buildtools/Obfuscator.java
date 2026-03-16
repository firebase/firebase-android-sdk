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

package com.google.firebase.crashlytics.buildtools;

public class Obfuscator {

  /**
   * This argument is an enum, not an arbitrary String: Crashlytics is able to deobfuscate the
   * following vendors.
   *
   * <p>Fortunately, all three of the above obfuscators are cross-compatible so, at present, the
   * type doesn't matter. But this may not be true in the future.
   */
  public enum Vendor {
    PROGUARD("proguard"),
    DEXGUARD("dexguard"),
    R8("R8");

    private final String name;

    Vendor(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private final Vendor vendor;
  private final String version;

  public Obfuscator(Vendor vendor, String version) {
    this.vendor = vendor;
    this.version = version;
  }

  public Vendor getVendor() {
    return vendor;
  }

  public String getVersion() {
    return version;
  }
}
