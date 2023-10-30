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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp

class ForegroundService : Service() {
  private val CHANNEL_ID = "CrashForegroundService"
  val receiver = CrashBroadcastReceiver()

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "Initializing app From ForegroundSErvice")
    FirebaseApp.initializeApp(this)
    createNotificationChannel()
    val pending =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val crashIntent = Intent(CrashBroadcastReceiver.CRASH_ACTION)
    val toastIntent = Intent(CrashBroadcastReceiver.TOAST_ACTION)

    val pendingCrash =
      PendingIntent.getBroadcast(this, 0, crashIntent, PendingIntent.FLAG_IMMUTABLE)
    val pendingToast =
      PendingIntent.getBroadcast(this, 0, toastIntent, PendingIntent.FLAG_IMMUTABLE)
    val pendingMsg =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, SecondActivity::class.java).setAction("MESSAGE"),
        PendingIntent.FLAG_IMMUTABLE
      )

    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Crash Test Notification Widget")
        .setContentText(intent?.getStringExtra("inputExtra"))
        .setContentIntent(pending)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setTicker("Crash Notification Widget Ticker")
        .addAction(R.drawable.ic_launcher_foreground, "CRASH!", pendingCrash)
        .addAction(R.drawable.ic_launcher_foreground, "TOAST!", pendingToast)
        .addAction(R.drawable.ic_launcher_foreground, "Send Message", pendingMsg)
        .build()

    startForeground(1, notification)
    return START_STICKY
  }

  override fun onBind(intent: Intent): IBinder? {
    return null
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "OnDestroy for ForegroundService")
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val serviceChannel =
        NotificationChannel(
          CHANNEL_ID,
          "Foreground Service Channel",
          NotificationManager.IMPORTANCE_DEFAULT
        )
      val manager = getSystemService(NotificationManager::class.java)
      manager!!.createNotificationChannel(serviceChannel)
    }
  }

  companion object {
    val TAG = "WidgetForegroundService"

    fun startService(context: Context, message: String) {
      Log.i(TAG, "Starting foreground serice")
      ContextCompat.startForegroundService(
        context,
        Intent(context, ForegroundService::class.java).putExtra("inputExtra", message)
      )
    }

    fun stopService(context: Context) {
      Log.i(TAG, "Stopping serice")
      context.stopService(Intent(context, ForegroundService::class.java))
    }
  }
}
