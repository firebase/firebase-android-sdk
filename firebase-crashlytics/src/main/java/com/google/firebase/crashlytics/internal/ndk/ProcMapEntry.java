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

/** Values parsed from a string entry in the crash data "maps" list. */
class ProcMapEntry {

  /** Entry's base address. */
  public final long address;

  /** Entry's calculated size. */
  public final long size;

  /** Permissions for the entry's associated file path. */
  public final String perms;

  /** Entry's associated file path. */
  public final String path;

  public ProcMapEntry(long address, long size, String perms, String path) {
    this.address = address;
    this.size = size;
    this.perms = perms;
    this.path = path;
  }
}
