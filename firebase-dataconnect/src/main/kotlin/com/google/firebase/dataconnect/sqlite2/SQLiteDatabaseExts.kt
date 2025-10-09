/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.sqlite2

import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQuery
import android.os.CancellationSignal
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import org.intellij.lang.annotations.Language

internal object SQLiteDatabaseExts {

  fun SQLiteDatabase.execSQL(logger: Logger, @Language("RoomSql") sql: String) {
    logger.debugSql(sql, null)
    execSQL(sql)
  }

  fun SQLiteDatabase.execSQL(
    logger: Logger,
    @Language("RoomSql") sql: String,
    bindArgs: Array<Any?>
  ) {
    logger.debugSql(sql, bindArgs)
    execSQL(sql, bindArgs)
  }

  fun <T> SQLiteDatabase.rawQuery(
    logger: Logger,
    @Language("RoomSql") sql: String,
    bindArgs: Array<Any?>? = null,
    cancellationSignal: CancellationSignal? = null,
    block: (Cursor) -> T
  ): T {
    logger.debugSql(sql, bindArgs)

    val cursor =
      if (bindArgs === null || bindArgs.isEmpty()) {
        rawQuery(sql, null, cancellationSignal)
      } else {
        // Use a QueryFactory hack to avoid having to convert all bindings to strings.
        // Firestore uses this hack too, in SQLitePersistence.java: http://goo.gle/46M38XN
        rawQueryWithFactory(
          { db, masterQuery, editTable, query ->
            bindArgs.bindTo(query)
            SQLiteCursor(masterQuery, editTable, query)
          },
          sql,
          null,
          null,
          cancellationSignal
        )
      }

    return cursor.use { block(it) }
  }

  private fun Logger.debugSql(@Language("RoomSql") sql: String, bindArgs: Array<Any?>?) = debug {
    if (bindArgs === null) {
      sql.trimIndent()
    } else if (sql.count { it == '?' } == bindArgs.size) {
      buildString {
        append(sql.trimIndent())
        bindArgs.forEach { bindArg ->
          val questionMarkIndex = indexOf('?')
          replace(questionMarkIndex, questionMarkIndex + 1, bindArg.escapedSQL())
        }
      }
    } else {
      buildString {
        append(sql.trimIndent())
        append(" bindArgs={")
        bindArgs.forEachIndexed { index, bindArg ->
          if (index > 0) {
            append(", ")
          }
          append(bindArg.escapedSQL())
        }
      }
    }
  }

  private fun Any?.escapedSQL(): String =
    when (this) {
      null -> "null"
      is String -> "'" + replace("'", "''") + "'"
      is Number -> toString()
      is ByteArray ->
        this.contentToString().let {
          if (it.length <= 10) {
            it
          } else {
            it.substring(0, 10) + "..."
          }
        }
      else -> this.toString()
    }

  private fun Array<Any?>.bindTo(query: SQLiteQuery) =
    query.run { forEachIndexed { index, value -> value.bindTo(query, index + 1) } }

  private fun Any?.bindTo(query: SQLiteQuery, index: Int) =
    query.run {
      when (val value = this@bindTo) {
        null -> bindNull(index)
        is CharSequence -> bindString(index, value.toString())
        is ByteArray -> bindBlob(index, value)
        is Int -> bindLong(index, value.toLong())
        is Long -> bindLong(index, value)
        is Float -> bindDouble(index, value.toDouble())
        is Double -> bindDouble(index, value)
        else ->
          throw IllegalArgumentException(
            "unsupported sqlite binding type ${value::class.qualifiedName} [e873hhvxm2]"
          )
      }
    }
}
