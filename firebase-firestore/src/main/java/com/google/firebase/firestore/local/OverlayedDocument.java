package com.google.firebase.firestore.local;

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.mutation.FieldMask;

public class OverlayedDocument {
  private Document overlay;
  private FieldMask mutatedFields;

  OverlayedDocument(Document overlay, FieldMask mutatedFields) {
    this.overlay = overlay;
    this.mutatedFields = mutatedFields;
  }

  public Document getOverlay() {
    return overlay;
  }

  public FieldMask getMutatedFields() {
    return mutatedFields;
  }
}
