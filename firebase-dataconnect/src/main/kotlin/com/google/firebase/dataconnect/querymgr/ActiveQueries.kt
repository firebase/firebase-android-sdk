package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.ReferenceCountedSet
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.toCompactString

internal class ActiveQueries(val dataConnect: FirebaseDataConnectImpl, parentLogger: Logger) :
  ReferenceCountedSet<ActiveQueryKey, ActiveQuery>() {

  private val logger =
    Logger("ActiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  override fun valueForKey(key: ActiveQueryKey) =
    ActiveQuery(
      dataConnect = dataConnect,
      operationName = key.operationName,
      variables = key.variables,
      parentLogger = logger,
    )

  override fun onAllocate(entry: Entry<ActiveQueryKey, ActiveQuery>) {
    logger.debug(
      "Registered ${entry.value.logger.nameWithId} (" +
        "operationName=${entry.key.operationName}, " +
        "variables=${entry.key.variables.toCompactString()})"
    )
  }

  override fun onFree(entry: Entry<ActiveQueryKey, ActiveQuery>) {
    logger.debug("Unregistered ${entry.value.logger.nameWithId}")
  }
}
