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

package com.google.firebase.crashlytics.ndk.utils;

public class TestMapEntry {
  public static final String FORMAT_ENTRY = "%08x-%08x %s %08x %s %d %s";

  private static final long DEFAULT_START_ADDRESS = 0L;
  private static final long DEFAULT_END_ADDRESS = 64L;
  private static final String DEFAULT_PERMS = "rwxp";
  private static final long DEFAULT_OFFSET = 0L;
  private static final String DEFAULT_DEVICE = "03:0c";
  private static final int DEFAULT_INODE = 7;
  private static final String DEFAULT_PATH = "/valid/path";

  private String outputFormat = FORMAT_ENTRY;

  public long startAddress;
  public long endAddress;
  public String permissions;
  public long offset;
  public String device;
  public int inode;
  public String path;

  public TestMapEntry() {
    this(
        DEFAULT_START_ADDRESS,
        DEFAULT_END_ADDRESS,
        DEFAULT_PERMS,
        DEFAULT_OFFSET,
        DEFAULT_DEVICE,
        DEFAULT_INODE,
        DEFAULT_PATH);
  }

  public TestMapEntry(long startAddress, long size, String path) {
    this(startAddress, startAddress + size, DEFAULT_PERMS, path);
  }

  public TestMapEntry(long startAddress, long size, String perms, String path) {
    this(
        startAddress,
        startAddress + size,
        perms,
        DEFAULT_OFFSET,
        DEFAULT_DEVICE,
        DEFAULT_INODE,
        path);
  }

  public TestMapEntry(
      long startAddress,
      long endAddress,
      String permissions,
      long offset,
      String device,
      int inode,
      String path) {
    this.startAddress = startAddress;
    this.endAddress = endAddress;
    this.permissions = permissions;
    this.offset = offset;
    this.device = device;
    this.inode = inode;
    this.path = path;
  }

  public void setToStringFormat(String format) {
    this.outputFormat = format;
  }

  @Override
  public String toString() {
    return String.format(
        outputFormat, startAddress, endAddress, permissions, offset, device, inode, path);
  }
}
