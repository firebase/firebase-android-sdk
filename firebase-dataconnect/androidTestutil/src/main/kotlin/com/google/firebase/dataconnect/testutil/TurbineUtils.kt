package com.google.firebase.dataconnect.testutil

import app.cash.turbine.ReceiveTurbine
import javax.annotation.CheckReturnValue

@CheckReturnValue
suspend fun <T> ReceiveTurbine<T>.skipItemsWhere(predicate: (T) -> Boolean): T {
  while (true) {
    val item = awaitItem()
    if (!predicate(item)) {
      return item
    }
  }
}
