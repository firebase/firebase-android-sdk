package com.google.firebase.sessions

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

/** Service for providing access to session data */
internal class SessionDataService : Service() {
  /** Target we publish for clients to send messages to IncomingHandler. */
  private lateinit var messenger: Messenger

  /** Handler of incoming messages from clients. */
  internal class IncomingHandler(
    context: Context,
    private val appContext: Context = context.applicationContext
  ) : Handler(Looper.getMainLooper()) { // TODO(rothbutter) probably want to use our own executor
    override fun handleMessage(msg: Message) {
      Log.i(TAG, "RECEIVED MESSAGE: $msg")
      when (msg.what) {
        else -> super.handleMessage(msg)
      }
    }
  }

  override fun onBind(intent: Intent): IBinder? {
    Log.i(TAG, "Service being bound")
    messenger = Messenger(IncomingHandler(this))
    return messenger.binder
  }

  internal companion object {
    const val TAG = "SessionDataService"
  }
}
