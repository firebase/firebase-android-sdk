package com.example.firebase.fiamui;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;

public class NoOpDisplayCallbacks implements FirebaseInAppMessagingDisplayCallbacks {
  @Override
  public Task<Void> impressionDetected() {
    return new TaskCompletionSource<Void>().getTask();
  }

  @Override
  public Task<Void> messageDismissed(InAppMessagingDismissType dismissType) {
    return new TaskCompletionSource<Void>().getTask();
  }

  @Override
  public Task<Void> messageClicked() {
    return new TaskCompletionSource<Void>().getTask();
  }

  @Override
  public Task<Void> displayErrorEncountered(InAppMessagingErrorReason InAppMessagingErrorReason) {
    return new TaskCompletionSource<Void>().getTask();
  }
}
