package com.google.firebase.firestore.remote;

import androidx.annotation.NonNull;

public class BloomFilterException extends Exception {
  public BloomFilterException(@NonNull String detailMessage) {
    super(detailMessage);
  }
}
