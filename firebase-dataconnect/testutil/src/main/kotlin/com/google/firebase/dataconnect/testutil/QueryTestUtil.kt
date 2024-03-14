package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.QuerySubscriptionResult

fun <Data, Variables> QuerySubscriptionResult<Data, Variables>.successOrThrow():
  QuerySubscriptionResult.Success<Data, Variables> =
  when (this) {
    is QuerySubscriptionResult.Success -> this
    is QuerySubscriptionResult.Failure -> throw exception
  }
