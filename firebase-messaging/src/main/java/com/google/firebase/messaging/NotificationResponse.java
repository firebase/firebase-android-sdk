// Copyright 2026 Google LLC
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
package com.google.firebase.messaging;

import android.app.Notification;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Response containing notification actions (post, cancel) to be executed by GMS Core. */
public final class NotificationResponse {

  @IntDef({ActionType.POST, ActionType.CANCEL})
  @Retention(RetentionPolicy.SOURCE)
  @interface ActionType {
    int POST = 1;
    int CANCEL = 2;
  }

  static class NotificationAction {
    @ActionType final int type;
    @Nullable final String tag;
    final int id;
    @Nullable final Notification notification;

    NotificationAction(
        @ActionType int type, @Nullable String tag, int id, @Nullable Notification notification) {
      this.type = type;
      this.tag = tag;
      this.id = id;
      this.notification = notification;
    }
  }

  private final List<NotificationAction> actions;

  private NotificationResponse(List<NotificationAction> actions) {
    this.actions = new ArrayList<>(actions);
  }

  /** Creates a response to post or update a notification. */
  @NonNull
  public static NotificationResponse create(
      @Nullable String tag, int id, @NonNull Notification notification) {
    return new NotificationResponse(
        Collections.singletonList(new NotificationAction(ActionType.POST, tag, id, notification)));
  }

  /** Creates a response to do nothing (suppress). */
  @NonNull
  public static NotificationResponse suppress() {
    return new NotificationResponse(Collections.emptyList());
  }

  /** Creates a response to cancel a previously posted notification. */
  @NonNull
  public static NotificationResponse createCancel(@Nullable String tag, int id) {
    return new NotificationResponse(
        Collections.singletonList(new NotificationAction(ActionType.CANCEL, tag, id, null)));
  }

  /** Adds a notification to be posted/updated. */
  @CanIgnoreReturnValue
  @NonNull
  public NotificationResponse add(
      @Nullable String tag, int id, @NonNull Notification notification) {
    this.actions.add(new NotificationAction(ActionType.POST, tag, id, notification));
    return this;
  }

  /** Adds the cancellation of a notification. */
  @CanIgnoreReturnValue
  @NonNull
  public NotificationResponse addCancel(@Nullable String tag, int id) {
    this.actions.add(new NotificationAction(ActionType.CANCEL, tag, id, null));
    return this;
  }

  List<NotificationAction> getActions() {
    return actions;
  }
}
