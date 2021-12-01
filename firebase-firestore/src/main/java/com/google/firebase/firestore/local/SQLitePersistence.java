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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteProgram;
import android.database.sqlite.SQLiteStatement;
import android.database.sqlite.SQLiteTransactionListener;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.util.Consumer;
import com.google.firebase.firestore.util.FileUtil;
import com.google.firebase.firestore.util.Function;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Supplier;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A SQLite-backed instance of Persistence.
 *
 * <p>In addition to implementations of the methods in the Persistence interface, also contains
 * helper routines that make dealing with SQLite much more pleasant.
 */
public final class SQLitePersistence extends Persistence {
  /**
   * The maximum number of bind args for a single statement. Set to 900 instead of 999 for safety.
   */
  public static final int MAX_ARGS = 900;

  /**
   * Creates the database name that is used to identify the database to be used with a Firestore
   * instance. Note that this needs to stay stable across releases. The database is uniquely
   * identified by a persistence key - usually the Firebase app name - and a DatabaseId (project and
   * database).
   *
   * <p>Format is "firestore.{persistence-key}.{project-id}.{database-id}".
   */
  @VisibleForTesting
  public static String databaseName(String persistenceKey, DatabaseId databaseId) {
    try {
      return "firestore."
          + URLEncoder.encode(persistenceKey, "utf-8")
          + "."
          + URLEncoder.encode(databaseId.getProjectId(), "utf-8")
          + "."
          + URLEncoder.encode(databaseId.getDatabaseId(), "utf-8");
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is a mandatory encoding supported on every platform.
      throw new AssertionError(e);
    }
  }

  private final OpenHelper opener;
  private final LocalSerializer serializer;
  private final SQLiteTargetCache targetCache;
  private final SQLiteBundleCache bundleCache;
  private final SQLiteRemoteDocumentCache remoteDocumentCache;
  private final SQLiteLruReferenceDelegate referenceDelegate;
  private final SQLiteTransactionListener transactionListener =
      new SQLiteTransactionListener() {
        @Override
        public void onBegin() {
          referenceDelegate.onTransactionStarted();
        }

        @Override
        public void onCommit() {
          referenceDelegate.onTransactionCommitted();
        }

        @Override
        public void onRollback() {}
      };

  private SQLiteDatabase db;
  private boolean started;

  public SQLitePersistence(
      Context context,
      String persistenceKey,
      DatabaseId databaseId,
      LocalSerializer serializer,
      LruGarbageCollector.Params params) {
    this(
        serializer,
        params,
        new OpenHelper(context, serializer, databaseName(persistenceKey, databaseId)));
  }

  public SQLitePersistence(
      LocalSerializer serializer, LruGarbageCollector.Params params, OpenHelper openHelper) {
    this.opener = openHelper;
    this.serializer = serializer;
    this.targetCache = new SQLiteTargetCache(this, this.serializer);
    this.bundleCache = new SQLiteBundleCache(this, this.serializer);
    this.remoteDocumentCache = new SQLiteRemoteDocumentCache(this, this.serializer);
    this.referenceDelegate = new SQLiteLruReferenceDelegate(this, params);
  }

  @Override
  public void start() {
    hardAssert(!started, "SQLitePersistence double-started!");
    started = true;
    try {
      db = opener.getWritableDatabase();
    } catch (SQLiteDatabaseLockedException e) {
      // TODO: Use a better exception type
      throw new RuntimeException(
          "Failed to gain exclusive lock to the Cloud Firestore client's offline persistence. This"
              + " generally means you are using Cloud Firestore from multiple processes in your"
              + " app. Keep in mind that multi-process Android apps execute the code in your"
              + " Application class in all processes, so you may need to avoid initializing"
              + " Cloud Firestore in your Application class. If you are intentionally using Cloud"
              + " Firestore from multiple processes, you can only enable offline persistence (that"
              + " is, call setPersistenceEnabled(true)) in one of them.",
          e);
    }
    targetCache.start();
    referenceDelegate.start(targetCache.getHighestListenSequenceNumber());
  }

  @Override
  public void shutdown() {
    hardAssert(started, "SQLitePersistence shutdown without start!");
    started = false;
    db.close();
    db = null;
  }

  @Override
  public boolean isStarted() {
    return started;
  }

  @Override
  public SQLiteLruReferenceDelegate getReferenceDelegate() {
    return referenceDelegate;
  }

  @Override
  MutationQueue getMutationQueue(User user, IndexManager indexManager) {
    return new SQLiteMutationQueue(this, serializer, user, indexManager);
  }

  @Override
  SQLiteTargetCache getTargetCache() {
    return targetCache;
  }

  @Override
  IndexManager getIndexManager(User user) {
    return new SQLiteIndexManager(this, serializer, user);
  }

  @Override
  BundleCache getBundleCache() {
    return bundleCache;
  }

  @Override
  DocumentOverlayCache getDocumentOverlay(User user) {
    return new SQLiteDocumentOverlayCache(this, this.serializer, user);
  }

  @Override
  OverlayMigrationManager getOverlayMigrationManager() {
    return new SQLiteOverlayMigrationManager(this);
  }

  @Override
  RemoteDocumentCache getRemoteDocumentCache() {
    return remoteDocumentCache;
  }

  @Override
  void runTransaction(String action, Runnable operation) {
    Logger.debug(TAG, "Starting transaction: %s", action);
    db.beginTransactionWithListener(transactionListener);
    try {
      operation.run();

      // Note that an exception in operation.run() will prevent this code from running.
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  <T> T runTransaction(String action, Supplier<T> operation) {
    Logger.debug(TAG, "Starting transaction: %s", action);
    T value = null;
    db.beginTransactionWithListener(transactionListener);
    try {
      value = operation.get();

      // Note that an exception in operation.run() will prevent this code from running.
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return value;
  }

  public static void clearPersistence(Context context, DatabaseId databaseId, String persistenceKey)
      throws FirebaseFirestoreException {
    String databaseName = SQLitePersistence.databaseName(persistenceKey, databaseId);
    String sqLitePath = context.getDatabasePath(databaseName).getPath();
    String journalPath = sqLitePath + "-journal";
    String walPath = sqLitePath + "-wal";

    File sqLiteFile = new File(sqLitePath);
    File journalFile = new File(journalPath);
    File walFile = new File(walPath);

    try {
      FileUtil.delete(sqLiteFile);
      FileUtil.delete(journalFile);
      FileUtil.delete(walFile);
    } catch (IOException e) {
      throw new FirebaseFirestoreException("Failed to clear persistence." + e, Code.UNKNOWN);
    }
  }

  long getByteSize() {
    return getPageCount() * getPageSize();
  }

  /**
   * Gets the page size of the database. Typically 4096.
   *
   * @see "https://www.sqlite.org/pragma.html#pragma_page_size"
   */
  private long getPageSize() {
    return query("PRAGMA page_size").firstValue(row -> row.getLong(/*column=*/ 0));
  }

  /**
   * Gets the number of pages in the database file. Multiplying this with the page size yields the
   * approximate size of the database on disk (including the WAL, if relevant).
   *
   * @see "https://www.sqlite.org/pragma.html#pragma_page_count."
   */
  private long getPageCount() {
    return query("PRAGMA page_count").firstValue(row -> row.getLong(/*column=*/ 0));
  }

  /**
   * A SQLiteOpenHelper that configures database connections just the way we like them, delegating
   * to SQLiteSchema to actually do the work of migration.
   *
   * <p>The order of events when opening a new connection is as follows:
   *
   * <ol>
   *   <li>New connection
   *   <li>onConfigure (API 16 and above)
   *   <li>onCreate / onUpgrade (optional; if version already matches these aren't called)
   *   <li>onOpen
   * </ol>
   *
   * <p>This OpenHelper attempts to obtain exclusive access to the database and attempts to do so as
   * early as possible. On Jelly Bean devices and above (some 98% of devices at time of writing)
   * this happens naturally during onConfigure. On pre-Jelly Bean devices all other methods ensure
   * that the configuration is applied before any action is taken.
   */
  @VisibleForTesting
  static class OpenHelper extends SQLiteOpenHelper {

    private final LocalSerializer serializer;
    private boolean configured;

    private OpenHelper(Context context, LocalSerializer serializer, String databaseName) {
      this(context, serializer, databaseName, SQLiteSchema.VERSION);
    }

    @VisibleForTesting
    OpenHelper(
        Context context, LocalSerializer serializer, String databaseName, int schemaVersion) {
      super(context, databaseName, null, schemaVersion);
      this.serializer = serializer;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
      // Note that this is only called automatically by the SQLiteOpenHelper base class on Jelly
      // Bean and above.
      configured = true;
      Cursor cursor = db.rawQuery("PRAGMA locking_mode = EXCLUSIVE", new String[0]);
      cursor.close();
    }

    /**
     * Ensures that onConfigure has been called. This should be called first from all methods other
     * than onConfigure to ensure that onConfigure has been called, even if running on a pre-Jelly
     * Bean device.
     */
    private void ensureConfigured(SQLiteDatabase db) {
      if (!configured) {
        onConfigure(db);
      }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      ensureConfigured(db);
      SQLiteSchema schema = new SQLiteSchema(db, serializer);
      schema.runSchemaUpgrades(0);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      ensureConfigured(db);
      SQLiteSchema schema = new SQLiteSchema(db, serializer);
      schema.runSchemaUpgrades(oldVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      ensureConfigured(db);

      // For now, we can safely do nothing.
      //
      // The only case that's possible at this point would be to downgrade from version 1 (present
      // in our first released version) to 0 (uninstalled). Nobody would want us to just wipe the
      // data so instead we just keep it around in the hope that they'll upgrade again :-).
      //
      // Note that if you uninstall a Firestore-based app, the database goes away completely. The
      // downgrade-then-upgrade case can only happen in very limited circumstances.
      //
      // We'll have to revisit this once we ship a migration past version 1, but this will
      // definitely be good enough for our initial launch.
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
      ensureConfigured(db);
    }
  }

  /**
   * Execute the given non-query SQL statement. Equivalent to {@code execute(prepare(sql), args)}.
   */
  void execute(String sql, Object... args) {
    // Note that unlike db.query and friends, execSQL already takes Object[] bindArgs so there's no
    // need to go through the bind dance below.
    db.execSQL(sql, args);
  }

  /** Prepare the given non-query SQL statement. */
  SQLiteStatement prepare(String sql) {
    return db.compileStatement(sql);
  }

  /**
   * Execute the given prepared non-query statement using the supplied bind arguments.
   *
   * @return The number of rows affected.
   */
  int execute(SQLiteStatement statement, Object... args) {
    statement.clearBindings();
    bind(statement, args);
    return statement.executeUpdateDelete();
  }

  /**
   * Creates a new {@link Query} for the given SQL query. Supply binding arguments and execute by
   * chaining further methods off the query.
   */
  Query query(String sql) {
    return new Query(db, sql);
  }

  /**
   * A wrapper around SQLiteDatabase's various query methods that serves to file down the rough
   * edges of using the SQLiteDatabase API. The wrapper provides:
   *
   * <ul>
   *   <li>Strongly-typed bind parameters (see {@link #binding}).
   *   <li>Exception-proof resource management, reducing try/finally boilerplate for each query.
   *   <li>Lambda-friendly result processing, reducing cursor boilerplate.
   * </ul>
   *
   * <p>Taken together, Query transforms code like this:
   *
   * <pre class="code">
   *   List<MutationBatch> result = new ArrayList<>();
   *   Cursor cursor = db.rawQuery(
   *       "SELECT mutations FROM mutations WHERE uid = ? AND batch_id <= ?",
   *       new String[] { uid, String.valueOf(batchId) });
   *   try {
   *     while (cursor.moveToNext()) {
   *       result.add(decodeMutationBatch(cursor.getBlob(0)));
   *     }
   *   } finally {
   *     cursor.close();
   *   }
   *   return result;
   * </pre>
   *
   * <p>Into code like this:
   *
   * <pre class="code">
   *   List<MutationBatch> result = new ArrayList<>();
   *   db.query("SELECT mutations FROM mutations WHERE uid = ? AND batch_id <= ?")
   *       .binding(uid, batchId)
   *       .forEach(row -> result.add(decodeMutationBatch(row.getBlob(0))));
   *   return result;
   * </pre>
   */
  static class Query {
    private final SQLiteDatabase db;
    private final String sql;
    private CursorFactory cursorFactory;

    Query(SQLiteDatabase db, String sql) {
      this.db = db;
      this.sql = sql;
    }

    /**
     * Uses the given binding arguments as positional parameters for the query.
     *
     * <p>Note that unlike {@link SQLiteDatabase#rawQuery}, this method takes Object binding
     * objects. Values in the <tt>args</tt> array need to be of a type that's usable in any of the
     * SQLiteProgram bindFoo methods.
     *
     * @return this Query object, for chaining.
     */
    Query binding(Object... args) {
      // This is gross, but the best way to preserve both the readability of the caller (since
      // values don't have be arbitrarily converted to Strings) and allows BLOBs to be used as
      // bind arguments.
      //
      // The trick here is that since db.query and db.rawQuery take String[] bind arguments, we
      // need some other way to bind. db.execSQL takes Object[] bind arguments but doesn't actually
      // allow querying because it doesn't return a Cursor. SQLiteQuery does allow typed bind
      // arguments, but isn't directly usable.
      //
      // However, you can get to the SQLiteQuery indirectly by supplying a CursorFactory to
      // db.rawQueryWithFactory. The factory's newCursor method will be called with a new
      // SQLiteQuery, and now we can bind with typed values.

      cursorFactory =
          (db1, masterQuery, editTable, query) -> {
            bind(query, args);
            return new SQLiteCursor(masterQuery, editTable, query);
          };
      return this;
    }

    /**
     * Runs the query, calling the consumer once for each row in the results.
     *
     * @param consumer A consumer that will receive the first row.
     * @return The number of rows processed
     */
    int forEach(Consumer<Cursor> consumer) {
      int rowsProcessed = 0;
      try (Cursor cursor = startQuery()) {
        while (cursor.moveToNext()) {
          ++rowsProcessed;
          consumer.accept(cursor);
        }
      }
      return rowsProcessed;
    }

    /**
     * Runs the query, calling the consumer on the first row of the results if one exists.
     *
     * @param consumer A consumer that will receive the first row.
     * @return The number of rows processed (either zero or one).
     */
    int first(Consumer<Cursor> consumer) {
      Cursor cursor = null;
      try {
        cursor = startQuery();
        if (cursor.moveToFirst()) {
          consumer.accept(cursor);
          return 1;
        }
        return 0;
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }

    /**
     * Runs the query and applies the function to the first row of the results if it exists.
     *
     * @param function A function to apply to the first row.
     * @param <T> The type of the return value of the function.
     * @return The result of the function application if there were any rows in the results or null
     *     otherwise.
     */
    @Nullable
    <T> T firstValue(Function<Cursor, T> function) {
      Cursor cursor = null;
      try {
        cursor = startQuery();
        if (cursor.moveToFirst()) {
          return function.apply(cursor);
        }
        return null;
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }

    /** Runs the query and returns true if the result was nonempty. */
    boolean isEmpty() {
      Cursor cursor = null;
      try {
        cursor = startQuery();
        return !cursor.moveToFirst();
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }

    /** Starts the query against the database, supplying binding arguments if they exist. */
    private Cursor startQuery() {
      if (cursorFactory != null) {
        return db.rawQueryWithFactory(cursorFactory, sql, null, null);
      } else {
        return db.rawQuery(sql, null);
      }
    }
  }

  /**
   * Encapsulates a query whose parameter list is so long that it might exceed SQLite limit.
   *
   * <p>SQLite limits maximum number of host parameters to 999 (see
   * https://www.sqlite.org/limits.html). This class wraps most of the messy details of splitting a
   * large query into several smaller ones.
   *
   * <p>The class is configured to contain a "template" for each subquery:
   *
   * <ol>
   *   <li>head -- the beginning of the query, will be the same for each subquery
   *   <li>tail -- the end of the query, also the same for each subquery
   * </ol>
   *
   * <p>Then the host parameters will be inserted in-between head and tail; if there are too many
   * arguments for a single query, several subqueries will be issued. Each subquery which will have
   * the following form:
   *
   * <p>[head][an auto-generated comma-separated list of '?' placeholders][tail]
   *
   * <p>To use this class, keep calling {@link #performNextSubquery}, which will issue the next
   * subquery, as long as {@link #hasMoreSubqueries} returns true. Note that if the parameter list
   * is empty, not even a single query will be issued.
   *
   * <p>For example, imagine for demonstration purposes that the limit were 2, and the {@code
   * LongQuery} was created like this:
   *
   * <pre class="code">
   *     String[] args = {"foo", "bar", "baz", "spam", "eggs"};
   *     LongQuery longQuery = new LongQuery(
   *         db,
   *         "SELECT name WHERE id in (",
   *         Arrays.asList(args),
   *         ")"
   *     );
   * </pre>
   *
   * <p>Assuming limit of 2, this query will issue three subqueries:
   *
   * <pre class="code">
   *     query.performNextSubquery(); // "SELECT name WHERE id in (?, ?)", binding "foo" and "bar"
   *     query.performNextSubquery(); // "SELECT name WHERE id in (?, ?)", binding "baz" and "spam"
   *     query.performNextSubquery(); // "SELECT name WHERE id in (?)", binding "eggs"
   * </pre>
   */
  static class LongQuery {
    private final SQLitePersistence db;
    // The non-changing beginning of each subquery.
    private final String head;
    // The non-changing end of each subquery.
    private final String tail;
    // Arguments that will be prepended in each subquery before the main argument list.
    private final List<Object> argsHead;

    private int subqueriesPerformed = 0;
    private final Iterator<Object> argsIter;

    // Limit for the number of host parameters beyond which a query will be split into several
    // subqueries. Deliberately set way below 999 as a safety measure because this class doesn't
    // attempt to check for placeholders in the query {@link head}; if it only relied on the number
    // of placeholders it itself generates, in that situation it would still exceed the SQLite
    // limit.
    private static final int LIMIT = 900;

    /**
     * Creates a new {@code LongQuery} with parameters that describe a template for creating each
     * subquery.
     *
     * @param db The database on which to execute the query.
     * @param head The non-changing beginning of the query; each subquery will begin with this.
     * @param allArgs The list of host parameters to bind. If the list size exceeds the limit,
     *     several subqueries will be issued, and the correct number of placeholders will be
     *     generated for each subquery.
     * @param tail The non-changing end of the query; each subquery will end with this.
     */
    LongQuery(SQLitePersistence db, String head, List<Object> allArgs, String tail) {
      this.db = db;
      this.head = head;
      this.argsHead = Collections.emptyList();
      this.tail = tail;

      argsIter = allArgs.iterator();
    }

    /**
     * The longer version of the constructor additionally takes {@code argsHead} parameter that
     * contains parameters that will be reissued in each subquery, i.e. subqueries take the form:
     *
     * <p>[head][argsHead][an auto-generated comma-separated list of '?' placeholders][tail]
     */
    LongQuery(
        SQLitePersistence db,
        String head,
        List<Object> argsHead,
        List<Object> allArgs,
        String tail) {
      this.db = db;
      this.head = head;
      this.argsHead = argsHead;
      this.tail = tail;

      argsIter = allArgs.iterator();
    }

    /** Whether {@link #performNextSubquery} can be called. */
    boolean hasMoreSubqueries() {
      return argsIter.hasNext();
    }

    /** Performs the next subquery and returns a {@link Query} object for method chaining. */
    Query performNextSubquery() {
      ++subqueriesPerformed;

      List<Object> subqueryArgs = new ArrayList<>(argsHead);
      StringBuilder placeholdersBuilder = new StringBuilder();
      for (int i = 0; argsIter.hasNext() && i < LIMIT - argsHead.size(); i++) {
        if (i > 0) {
          placeholdersBuilder.append(", ");
        }
        placeholdersBuilder.append("?");

        subqueryArgs.add(argsIter.next());
      }
      String placeholders = placeholdersBuilder.toString();

      return db.query(head + placeholders + tail).binding(subqueryArgs.toArray());
    }

    /** How many subqueries were performed. */
    int getSubqueriesPerformed() {
      return subqueriesPerformed;
    }
  }

  /**
   * Binds the given arguments to the given SQLite statement or query.
   *
   * <p>This method helps work around the fact that all of the querying methods on SQLiteDatabase
   * take an array of strings for bind arguments. Most values can be straightforwardly converted to
   * strings except BLOB literals, which must be base64 encoded and is just too painful.
   *
   * <p>It's possible to bind using typed arguments (including BLOBs) using SQLiteProgram objects.
   * This method bridges the gap by examining the types of the bindArgs and calling to the
   * appropriate bind method on the program.
   */
  private static void bind(SQLiteProgram program, Object[] bindArgs) {
    for (int i = 0; i < bindArgs.length; i++) {
      Object arg = bindArgs[i];
      if (arg == null) {
        program.bindNull(i + 1);
      } else if (arg instanceof String) {
        program.bindString(i + 1, (String) arg);
      } else if (arg instanceof Integer) {
        program.bindLong(i + 1, (Integer) arg);
      } else if (arg instanceof Long) {
        program.bindLong(i + 1, (Long) arg);
      } else if (arg instanceof Double) {
        program.bindDouble(i + 1, (Double) arg);
      } else if (arg instanceof byte[]) {
        program.bindBlob(i + 1, (byte[]) arg);
      } else {
        throw fail("Unknown argument %s of type %s", arg, arg.getClass());
      }
    }
  }
}
