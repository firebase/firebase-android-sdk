package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import java.util.HashMap;
import java.util.Map;

public class MemoryDocumentOverlays implements DocumentOverlays {
  private Map<String, Map<DocumentKey, Mutation>> overlays = new HashMap<>();

  @Nullable
  @Override
  public Mutation getOverlayMutation(String uid, DocumentKey key) {
    if (overlays.containsKey(uid)) {
      return overlays.get(uid).get(key);
    }

    return null;
  }

  @Override
  public void saveOverlayMutation(String uid, DocumentKey key, Mutation mutation) {
    if (!overlays.containsKey(uid)) {
      overlays.put(uid, new HashMap<>());
    }

    overlays.get(uid).put(key, mutation);
  }

  @Override
  public void removeOverlayMutation(String uid, DocumentKey key) {
    if (overlays.containsKey(uid)) {
      overlays.get(uid).remove(key);
      if (overlays.get(uid).isEmpty()) {
        overlays.remove(uid);
      }
    }
  }
}
