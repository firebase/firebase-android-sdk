// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution;

import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;

/** An enum specifying the level of interruption of a notification when it is created. */
public enum InterruptionLevel {

  /**
   * Minimum interruption level.
   *
   * <p>Translates to {@link NotificationManager#IMPORTANCE_MIN} on Android O+ and {@link
   * NotificationCompat#PRIORITY_MIN} on older platforms.
   */
  MIN(NotificationManager.IMPORTANCE_MIN, NotificationCompat.PRIORITY_MIN),

  /**
   * Low interruption level.
   *
   * <p>Translates to {@link NotificationManager#IMPORTANCE_LOW} on Android O+ and {@link
   * NotificationCompat#PRIORITY_LOW} on older platforms.
   */
  LOW(NotificationManager.IMPORTANCE_LOW, NotificationCompat.PRIORITY_LOW),

  /**
   * Default interruption level.
   *
   * <p>Translates to {@link NotificationManager#IMPORTANCE_DEFAULT} on Android O+ and {@link
   * NotificationCompat#PRIORITY_DEFAULT} on older platforms.
   */
  DEFAULT(NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.PRIORITY_DEFAULT),

  /**
   * High interruption level.
   *
   * <p>Translates to {@link NotificationManager#IMPORTANCE_HIGH} on Android O+ and {@link
   * NotificationCompat#PRIORITY_HIGH} on older platforms.
   */
  HIGH(NotificationManager.IMPORTANCE_HIGH, NotificationCompat.PRIORITY_HIGH),

  /**
   * Maximum interruption level.
   *
   * <p>Translates to {@link NotificationManager#IMPORTANCE_HIGH} on Android O+ and {@link
   * NotificationCompat#PRIORITY_MAX} on older platforms.
   */
  MAX(NotificationManager.IMPORTANCE_HIGH, NotificationCompat.PRIORITY_MAX);

  /**
   * The notification channel importance corresponding to this interruption level on Android O+.
   *
   * @hide
   */
  public final int channelImportance;

  /**
   * The notification priority corresponding to this interruption level on older platforms.
   *
   * @hide
   */
  public final int notificationPriority;

  InterruptionLevel(int channelImportance, int notificationPriority) {
    this.channelImportance = channelImportance;
    this.notificationPriority = notificationPriority;
  }
}
