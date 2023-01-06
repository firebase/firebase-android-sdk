// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.internal;

import androidx.annotation.VisibleForTesting;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingClickListener;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDismissListener;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayErrorListener;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingImpressionListener;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.InAppMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A class used to manage and schedule events to registered (ie: developer-defined) or expensive
 * listeners
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
public class DeveloperListenerManager {

  private final Executor backgroundExecutor;
  private Map<FirebaseInAppMessagingClickListener, ClicksExecutorAndListener>
      registeredClickListeners = new HashMap<>();
  private Map<FirebaseInAppMessagingDismissListener, DismissExecutorAndListener>
      registeredDismissListeners = new HashMap<>();
  private Map<FirebaseInAppMessagingDisplayErrorListener, ErrorsExecutorAndListener>
      registeredErrorListeners = new HashMap<>();
  private Map<FirebaseInAppMessagingImpressionListener, ImpressionExecutorAndListener>
      registeredImpressionListeners = new HashMap<>();

  public DeveloperListenerManager(@Background Executor backgroundExecutor) {
    this.backgroundExecutor = backgroundExecutor;
  }

  // Used internally by MetricsLoggerClient
  public void impressionDetected(InAppMessage inAppMessage) {
    for (ImpressionExecutorAndListener listener : registeredImpressionListeners.values()) {
      listener
          .withExecutor(backgroundExecutor)
          .execute(() -> listener.getListener().impressionDetected(inAppMessage));
    }
  }

  public void displayErrorEncountered(
      InAppMessage inAppMessage,
      FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason errorReason) {
    for (ErrorsExecutorAndListener listener : registeredErrorListeners.values()) {
      listener
          .withExecutor(backgroundExecutor)
          .execute(() -> listener.getListener().displayErrorEncountered(inAppMessage, errorReason));
    }
  }

  public void messageClicked(InAppMessage inAppMessage, Action action) {
    for (ClicksExecutorAndListener listener : registeredClickListeners.values()) {
      listener
          .withExecutor(backgroundExecutor)
          .execute(() -> listener.getListener().messageClicked(inAppMessage, action));
    }
  }

  public void messageDismissed(InAppMessage inAppMessage) {
    for (DismissExecutorAndListener listener : registeredDismissListeners.values()) {
      listener
          .withExecutor(backgroundExecutor)
          .execute(() -> listener.getListener().messageDismissed(inAppMessage));
    }
  }

  // pass through from FirebaseInAppMessaging public api
  public void addImpressionListener(FirebaseInAppMessagingImpressionListener impressionListener) {
    registeredImpressionListeners.put(
        impressionListener, new ImpressionExecutorAndListener(impressionListener));
  }

  public void addClickListener(FirebaseInAppMessagingClickListener clickListener) {
    registeredClickListeners.put(clickListener, new ClicksExecutorAndListener(clickListener));
  }

  public void addDismissListener(FirebaseInAppMessagingDismissListener dismissListener) {
    registeredDismissListeners.put(
        dismissListener, new DismissExecutorAndListener(dismissListener));
  }

  public void addDisplayErrorListener(
      FirebaseInAppMessagingDisplayErrorListener displayErrorListener) {
    registeredErrorListeners.put(
        displayErrorListener, new ErrorsExecutorAndListener(displayErrorListener));
  }

  // Executed with provided executor
  public void addImpressionListener(
      FirebaseInAppMessagingImpressionListener impressionListener, Executor executor) {
    registeredImpressionListeners.put(
        impressionListener, new ImpressionExecutorAndListener(impressionListener, executor));
  }

  public void addClickListener(
      FirebaseInAppMessagingClickListener clickListener, Executor executor) {
    registeredClickListeners.put(
        clickListener, new ClicksExecutorAndListener(clickListener, executor));
  }

  public void addDismissListener(
      FirebaseInAppMessagingDismissListener dismissListener, Executor executor) {
    registeredDismissListeners.put(
        dismissListener, new DismissExecutorAndListener(dismissListener, executor));
  }

  public void addDisplayErrorListener(
      FirebaseInAppMessagingDisplayErrorListener displayErrorListener, Executor executor) {
    registeredErrorListeners.put(
        displayErrorListener, new ErrorsExecutorAndListener(displayErrorListener, executor));
  }

  // Removing individual listeners:
  public void removeImpressionListener(
      FirebaseInAppMessagingImpressionListener impressionListener) {
    registeredImpressionListeners.remove(impressionListener);
  }

  public void removeClickListener(FirebaseInAppMessagingClickListener clickListener) {
    registeredClickListeners.remove(clickListener);
  }

  public void removeDisplayErrorListener(
      FirebaseInAppMessagingDisplayErrorListener displayErrorListener) {
    registeredErrorListeners.remove(displayErrorListener);
  }

  public void removeDismissListener(FirebaseInAppMessagingDismissListener dismissListener) {
    registeredDismissListeners.remove(dismissListener);
  }

  public void removeAllListeners() {
    registeredClickListeners.clear();
    registeredImpressionListeners.clear();
    registeredErrorListeners.clear();
    registeredDismissListeners.clear();
  }

  @VisibleForTesting
  public Map getAllListeners() {
    Map listeners = new HashMap();
    listeners.putAll(registeredClickListeners);
    listeners.putAll(registeredImpressionListeners);
    listeners.putAll(registeredErrorListeners);
    listeners.putAll(registeredDismissListeners);
    return listeners;
  }

  private abstract static class ExecutorAndListener<T> {

    private final Executor executor;

    public abstract T getListener();

    public Executor withExecutor(Executor defaultExecutor) {
      if (executor == null) {
        return defaultExecutor;
      }
      return executor;
    }

    public ExecutorAndListener(Executor e) {
      this.executor = e;
    }
  }

  private static class ImpressionExecutorAndListener
      extends ExecutorAndListener<FirebaseInAppMessagingImpressionListener> {
    FirebaseInAppMessagingImpressionListener listener;

    public ImpressionExecutorAndListener(
        FirebaseInAppMessagingImpressionListener listener, Executor e) {
      super(e);
      this.listener = listener;
    }

    public ImpressionExecutorAndListener(FirebaseInAppMessagingImpressionListener listener) {
      super(null);
      this.listener = listener;
    }

    @Override
    public FirebaseInAppMessagingImpressionListener getListener() {
      return listener;
    }
  }

  private static class ClicksExecutorAndListener
      extends ExecutorAndListener<FirebaseInAppMessagingClickListener> {
    FirebaseInAppMessagingClickListener listener;

    public ClicksExecutorAndListener(FirebaseInAppMessagingClickListener listener, Executor e) {
      super(e);
      this.listener = listener;
    }

    public ClicksExecutorAndListener(FirebaseInAppMessagingClickListener listener) {
      super(null);
      this.listener = listener;
    }

    @Override
    public FirebaseInAppMessagingClickListener getListener() {
      return listener;
    }
  }

  private static class DismissExecutorAndListener
      extends ExecutorAndListener<FirebaseInAppMessagingDismissListener> {
    FirebaseInAppMessagingDismissListener listener;

    public DismissExecutorAndListener(FirebaseInAppMessagingDismissListener listener, Executor e) {
      super(e);
      this.listener = listener;
    }

    public DismissExecutorAndListener(FirebaseInAppMessagingDismissListener listener) {
      super(null);
      this.listener = listener;
    }

    @Override
    public FirebaseInAppMessagingDismissListener getListener() {
      return listener;
    }
  }

  private static class ErrorsExecutorAndListener
      extends ExecutorAndListener<FirebaseInAppMessagingDisplayErrorListener> {
    FirebaseInAppMessagingDisplayErrorListener listener;

    public ErrorsExecutorAndListener(
        FirebaseInAppMessagingDisplayErrorListener listener, Executor e) {
      super(e);
      this.listener = listener;
    }

    public ErrorsExecutorAndListener(FirebaseInAppMessagingDisplayErrorListener listener) {
      super(null);
      this.listener = listener;
    }

    @Override
    public FirebaseInAppMessagingDisplayErrorListener getListener() {
      return listener;
    }
  }
}
