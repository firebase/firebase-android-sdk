package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import com.google.firebase.firestore.core.FirestoreClient;

// TODO(csi): Remove the `hide` and scope annotations.
/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PersistentCacheManager {
  @NonNull private FirestoreClient client;

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public PersistentCacheManager(FirestoreClient client) {
    this.client = client;
  }

  public void setAutomaticIndexingEnabled(boolean isEnabled) {
    client.setAutomaticIndexingEnabled(isEnabled);
  }
}
