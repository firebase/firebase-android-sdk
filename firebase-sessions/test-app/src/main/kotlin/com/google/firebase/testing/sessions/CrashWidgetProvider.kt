/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.testing.sessions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.widget.RemoteViews
import com.google.firebase.FirebaseApp
import java.util.Date
import java.util.Locale

/** Provides homescreen widget for the test app. */
class CrashWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    FirebaseApp.initializeApp(context)

    appWidgetIds.forEach { appWidgetId ->
      // Get the layout for the widget and attach an on-click listener
      // to the button.
      val views: RemoteViews =
        RemoteViews(context.packageName, R.layout.crash_widget).apply {
          setOnClickPendingIntent(R.id.widgetCrashButton, getPendingCrashIntent(context))
          setTextViewText(R.id.widgetTimeText, getDateText())
        }

      // Tell the AppWidgetManager to perform an update on the current
      // widget.
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  override fun onReceive(context: Context, intent: Intent): Unit {
    super.onReceive(context, intent)

    if (CRASH_BUTTON_CLICK == intent.getAction()) {
      throw RuntimeException("CRASHED FROM WIDGET")
    }
  }

  fun getPendingCrashIntent(context: Context): PendingIntent {
    val intent = Intent(context, CrashWidgetProvider::class.java)
    intent.setAction(CRASH_BUTTON_CLICK)
    return PendingIntent.getBroadcast(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  companion object {
    val CRASH_BUTTON_CLICK = "widgetCrashButtonClick"

    fun getDateText(): String =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
      else "unknown"
  }
}
