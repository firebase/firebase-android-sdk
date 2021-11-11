package com.google.android.datatransport.runtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TestDestination implements Destination {
  private final String name;
  private final byte[] extras;

  public TestDestination(String name, byte[] extras) {
    this.name = name;
    this.extras = extras;
  }

  @NonNull
  @Override
  public String getName() {
    return name;
  }

  @Nullable
  @Override
  public byte[] getExtras() {
    return extras;
  }
}
