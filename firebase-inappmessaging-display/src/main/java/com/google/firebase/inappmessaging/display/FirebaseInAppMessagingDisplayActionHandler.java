package com.google.firebase.inappmessaging.display;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.google.firebase.inappmessaging.model.Action;

/**
 * Handles message element (image, button) clicks.
 */
public interface FirebaseInAppMessagingDisplayActionHandler {
  void handleAction(@NonNull Activity activity, @NonNull Action action);
}
