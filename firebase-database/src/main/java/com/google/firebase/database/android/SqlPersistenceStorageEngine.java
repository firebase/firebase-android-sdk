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

package com.google.firebase.database.android;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.core.persistence.PersistenceStorageEngine;
import com.google.firebase.database.core.persistence.PruneForest;
import com.google.firebase.database.core.persistence.TrackedQuery;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.utilities.NodeSizeEstimator;
import com.google.firebase.database.core.utilities.Pair;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.ChildrenNode;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.util.JsonMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This class is an Android/SQL-backed implementation of PersistenceStorageEngine.
 *
 * <p>The implementation uses 3 tables for persistence: - writes: A list of all currently
 * outstanding (visible) writes. A row represents a single write containing a unique-across-restarts
 * write id, the path string, the type which can be 'o' for an overwrite or 'm' for a merge, and the
 * serialized node wrapped. Part number is NULL for normal writes. Large writes (>256K) are split
 * into multiple rows with increasing part number.
 *
 * <p>- serverCache: Cached nodes from the server. A row represents a node at a path. To allow for
 * fast writes, a row added later can overwrite a part of a previous row at a higher path. When
 * loading the cache, all rows at a path and deeper need to be combined to get the latest cached
 * node. To ensure this representation is unambiguous, updates for a given path need to delete any
 * node at the same or any deeper path. There is a size threshold after which children-nodes are
 * split up and each child is saved individually (and can be split up again). This is so deeper
 * reads don't need to read massive amounts of data and single rows don't have excessive amount of
 * data in them.
 *
 * <p>- trackedQueries: Queries tracked in our cache. Each row has a unique-across-restarts id, the
 * path/query and metadata about the query (whether we have complete data, when it was last used,
 * and whether it's currently active). These tracked queries are used to determine what serverCache
 * data exists, whether it's complete, and what serverCache data can be pruned.
 *
 * <p>- trackedKeys: Keys in tracked queries. For each query in trackedQueries that is filtered
 * (i.e. not a loadsAllData() query), we'll track which keys are in the query. This allows us to
 * re-load only the keys of interest when restoring the query, as well as prune data for keys that
 * aren't tracked by any query.
 *
 * <p>As mentioned earlier, for "fast writes", serverCache may end up with overlapping rows. As an
 * example, you might have the following rows: /foo/: {"bar": 1, "baz": 2, "qux": 3} /foo/bar/: 4
 * /foo/qux/: null /foo/quu/: 5 => yields {"bar": 4, "baz": 2, "quu": 5} at /foo
 *
 * <p>Large serverCache leaf nodes might be split into multiple parts to avoid the limitation of
 * ~1MB per row for the SQL cursor. Consecutive leaf node parts will be saved as .part-XXXX without
 * a trailing slash at the end. /leaf/node/path/.part-0000 /leaf/node/path/.part-0001 ...
 *
 * <p>To ensure prefix queries work on paths, each path must start and end with a '/'.
 *
 * <p>Schema: - writes table + id: unique id across restarts + path: path for this write as string +
 * node: serialized node/merge-map as JSON (utf-8) bytes + part: The part number for
 * multi-part/split writes starting with 0, NULL if not split + type: 'o' for overwrite and 'm' for
 * merge - serverCache + path: path for this node as string + value: serialized node as JSON (utf-8)
 * bytes - trackedQueries + id: unique id across restarts + path: Path of query. + query: A
 * serialization of the query parameters. + lastUse: When this query was last used (e.g. there was
 * an active listener). + complete: Whether serverCache contains complete data for the query. +
 * active: Whether we have an active listener for the query. - trackedKeys + id: id of the
 * trackedQuery for which this is a tracked key. + key: The tracked key belonging to the
 * trackedQuery identified by id.
 */
public class SqlPersistenceStorageEngine implements PersistenceStorageEngine {
  private static final String CREATE_SERVER_CACHE =
      "CREATE TABLE serverCache (path TEXT PRIMARY KEY, value BLOB);";

  private static final String SERVER_CACHE_TABLE = "serverCache";
  private static final String PATH_COLUMN_NAME = "path";
  private static final String VALUE_COLUMN_NAME = "value";

  private static final String CREATE_WRITES =
      "CREATE TABLE writes (id INTEGER, path TEXT, type TEXT, part INTEGER, node BLOB, UNIQUE "
          + "(id, part));";
  private static final String WRITES_TABLE = "writes";
  private static final String WRITE_ID_COLUMN_NAME = "id";
  private static final String WRITE_NODE_COLUMN_NAME = "node";
  private static final String WRITE_PART_COLUMN_NAME = "part";
  private static final String WRITE_TYPE_COLUMN_NAME = "type";

  private static final String WRITE_TYPE_OVERWRITE = "o";
  private static final String WRITE_TYPE_MERGE = "m";

  private static final String CREATE_TRACKED_QUERIES =
      "CREATE TABLE trackedQueries (id INTEGER PRIMARY KEY, path TEXT, "
          + "queryParams TEXT, lastUse INTEGER, complete INTEGER, active INTEGER);";
  private static final String TRACKED_QUERY_TABLE = "trackedQueries";
  private static final String TRACKED_QUERY_ID_COLUMN_NAME = "id";
  private static final String TRACKED_QUERY_PATH_COLUMN_NAME = "path";
  private static final String TRACKED_QUERY_PARAMS_COLUMN_NAME = "queryParams";
  private static final String TRACKED_QUERY_LAST_USE_COLUMN_NAME = "lastUse";
  private static final String TRACKED_QUERY_COMPLETE_COLUMN_NAME = "complete";
  private static final String TRACKED_QUERY_ACTIVE_COLUMN_NAME = "active";

  private static final String CREATE_TRACKED_KEYS =
      "CREATE TABLE trackedKeys (id INTEGER, key TEXT);";
  private static final String TRACKED_KEYS_TABLE = "trackedKeys";
  private static final String TRACKED_KEYS_ID_COLUMN_NAME = "id";
  private static final String TRACKED_KEYS_KEY_COLUMN_NAME = "key";

  private static final String ROW_ID_COLUMN_NAME = "rowid";

  /** Children of children nodes above this serialized size in bytes will be saved individually. */
  private static final int CHILDREN_NODE_SPLIT_SIZE_THRESHOLD = 16 * 1024;

  /** Serialized leaf nodes above this size will be split into multiple parts */
  private static final int ROW_SPLIT_SIZE = 256 * 1024;

  private static final String PART_KEY_FORMAT = ".part-%04d";
  private static final String FIRST_PART_KEY = ".part-0000";
  private static final String PART_KEY_PREFIX = ".part-";

  private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

  private static class PersistentCacheOpenHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 2;

    public PersistentCacheOpenHelper(Context context, String cacheId) {
      super(context, cacheId, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(CREATE_SERVER_CACHE);
      db.execSQL(CREATE_WRITES);
      db.execSQL(CREATE_TRACKED_QUERIES);
      db.execSQL(CREATE_TRACKED_KEYS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      hardAssert(
          newVersion == DATABASE_VERSION, "Why is onUpgrade() called with a different version?");
      if (oldVersion <= 1) {
        // Leave old writes table.

        // Recreate server cache.
        dropTable(db, SERVER_CACHE_TABLE);
        db.execSQL(CREATE_SERVER_CACHE);

        // Drop old completeness table.
        dropTable(db, "complete");

        // Create tracked queries/keys.
        db.execSQL(CREATE_TRACKED_KEYS);
        db.execSQL(CREATE_TRACKED_QUERIES);
      } else {
        throw new AssertionError("We don't handle upgrading to " + newVersion);
      }
    }

    private void dropTable(SQLiteDatabase db, String table) {
      db.execSQL("DROP TABLE IF EXISTS " + table);
    }
  }

  private static final String LOGGER_COMPONENT = "Persistence";

  private final SQLiteDatabase database;
  private final LogWrapper logger;
  private boolean insideTransaction;
  private long transactionStart = 0;

  public SqlPersistenceStorageEngine(
      Context context, com.google.firebase.database.core.Context firebaseContext, String cacheId) {
    String sanitizedCacheId;
    try {
      sanitizedCacheId = URLEncoder.encode(cacheId, "utf-8");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.logger = firebaseContext.getLogger(LOGGER_COMPONENT);
    this.database = this.openDatabase(context, sanitizedCacheId);
  }

  @Override
  public void saveUserOverwrite(Path path, Node node, long writeId) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    byte[] serializedNode = serializeObject(node.getValue(true));
    saveWrite(path, writeId, WRITE_TYPE_OVERWRITE, serializedNode);
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(String.format(Locale.US, "Persisted user overwrite in %dms", duration));
    }
  }

  @Override
  public void saveUserMerge(Path path, CompoundWrite children, long writeId) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    byte[] serializedNode = serializeObject(children.getValue(true));
    saveWrite(path, writeId, WRITE_TYPE_MERGE, serializedNode);
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(String.format(Locale.US, "Persisted user merge in %dms", duration));
    }
  }

  @Override
  public void removeUserWrite(long writeId) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    int count = database.delete(WRITES_TABLE, "id = ?", new String[] {String.valueOf(writeId)});
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US, "Deleted %d write(s) with writeId %d in %dms", count, writeId, duration));
    }
  }

  @Override
  public List<UserWriteRecord> loadUserWrites() {
    String[] columns =
        new String[] {
          WRITE_ID_COLUMN_NAME,
          PATH_COLUMN_NAME,
          WRITE_TYPE_COLUMN_NAME,
          WRITE_PART_COLUMN_NAME,
          WRITE_NODE_COLUMN_NAME
        };
    long start = System.currentTimeMillis();
    Cursor cursor =
        database.query(
            WRITES_TABLE,
            columns,
            null,
            null,
            null,
            null,
            WRITE_ID_COLUMN_NAME + ", " + WRITE_PART_COLUMN_NAME);

    List<UserWriteRecord> writes = new ArrayList<UserWriteRecord>();
    try {
      while (cursor.moveToNext()) {
        long writeId = cursor.getLong(0);
        Path path = new Path(cursor.getString(1));
        String type = cursor.getString(2);
        byte[] serialized;
        if (cursor.isNull(3)) {
          // single part write
          serialized = cursor.getBlob(4);
        } else {
          // multi part write
          List<byte[]> parts = new ArrayList<byte[]>();
          do {
            parts.add(cursor.getBlob(4));
          } while (cursor.moveToNext() && cursor.getLong(0) == writeId);
          // move cursor one back so it points to last part of writes
          cursor.moveToPrevious();
          serialized = joinBytes(parts);
        }
        String serializedString = new String(serialized, UTF8_CHARSET);
        Object writeValue = JsonMapper.parseJsonValue(serializedString);
        UserWriteRecord record;
        if (WRITE_TYPE_OVERWRITE.equals(type)) {
          Node set = NodeUtilities.NodeFromJSON(writeValue);
          record = new UserWriteRecord(writeId, path, set, /*visible=*/ true);
        } else if (WRITE_TYPE_MERGE.equals(type)) {
          @SuppressWarnings("unchecked")
          CompoundWrite merge = CompoundWrite.fromValue((Map<String, Object>) writeValue);
          record = new UserWriteRecord(writeId, path, merge);
        } else {
          throw new IllegalStateException("Got invalid write type: " + type);
        }
        writes.add(record);
      }
      long duration = System.currentTimeMillis() - start;
      if (logger.logsDebug()) {
        logger.debug(String.format(Locale.US, "Loaded %d writes in %dms", writes.size(), duration));
      }
      return writes;
    } catch (IOException e) {
      throw new RuntimeException("Failed to load writes", e);
    } finally {
      cursor.close();
    }
  }

  private void saveWrite(Path path, long writeId, String type, byte[] serializedWrite) {
    verifyInsideTransaction();
    database.delete(
        WRITES_TABLE, WRITE_ID_COLUMN_NAME + " = ?", new String[] {String.valueOf(writeId)});
    if (serializedWrite.length >= ROW_SPLIT_SIZE) {
      List<byte[]> parts = splitBytes(serializedWrite, ROW_SPLIT_SIZE);
      for (int i = 0; i < parts.size(); i++) {
        ContentValues values = new ContentValues();
        values.put(WRITE_ID_COLUMN_NAME, writeId);
        values.put(PATH_COLUMN_NAME, pathToKey(path));
        values.put(WRITE_TYPE_COLUMN_NAME, type);
        values.put(WRITE_PART_COLUMN_NAME, i);
        values.put(WRITE_NODE_COLUMN_NAME, parts.get(i));
        database.insertWithOnConflict(WRITES_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
      }
    } else {
      ContentValues values = new ContentValues();
      values.put(WRITE_ID_COLUMN_NAME, writeId);
      values.put(PATH_COLUMN_NAME, pathToKey(path));
      values.put(WRITE_TYPE_COLUMN_NAME, type);
      values.put(WRITE_PART_COLUMN_NAME, (Integer) null);
      values.put(WRITE_NODE_COLUMN_NAME, serializedWrite);
      database.insertWithOnConflict(WRITES_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
  }

  @Override
  public Node serverCache(Path path) {
    return loadNested(path);
  }

  @Override
  public void overwriteServerCache(Path path, Node node) {
    verifyInsideTransaction();
    updateServerCache(path, node, /*merge=*/ false);
  }

  @Override
  public void mergeIntoServerCache(Path path, Node node) {
    verifyInsideTransaction();
    updateServerCache(path, node, /*merge=*/ true);
  }

  private void updateServerCache(Path path, Node node, boolean merge) {
    long start = System.currentTimeMillis();
    int removedRows;
    int savedRows;
    if (!merge) {
      removedRows = removeNested(SERVER_CACHE_TABLE, path);
      savedRows = saveNested(path, node);
    } else {
      removedRows = 0;
      savedRows = 0;
      for (NamedNode child : node) {
        removedRows += removeNested(SERVER_CACHE_TABLE, path.child(child.getName()));
        savedRows += saveNested(path.child(child.getName()), child.getNode());
      }
    }
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US,
              "Persisted a total of %d rows and deleted %d rows for a set at %s in %dms",
              savedRows,
              removedRows,
              path.toString(),
              duration));
    }
  }

  @Override
  public void mergeIntoServerCache(Path path, CompoundWrite children) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    int savedRows = 0;
    int removedRows = 0;
    for (Map.Entry<Path, Node> entry : children) {
      removedRows += removeNested(SERVER_CACHE_TABLE, path.child(entry.getKey()));
      savedRows += saveNested(path.child(entry.getKey()), entry.getValue());
    }
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US,
              "Persisted a total of %d rows and deleted %d rows for a merge at %s in %dms",
              savedRows,
              removedRows,
              path.toString(),
              duration));
    }
  }

  @Override
  public long serverCacheEstimatedSizeInBytes() {
    String query =
        String.format(
            "SELECT sum(length(%s) + length(%s)) FROM %s",
            VALUE_COLUMN_NAME, PATH_COLUMN_NAME, SERVER_CACHE_TABLE);
    Cursor cursor = database.rawQuery(query, null);
    try {
      if (cursor.moveToFirst()) {
        return cursor.getLong(0); // corresponds to the sum in the query
      } else {
        throw new IllegalStateException("Couldn't read database result!");
      }
    } finally {
      cursor.close();
    }
  }

  @Override
  public void saveTrackedQuery(TrackedQuery trackedQuery) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    ContentValues values = new ContentValues();
    values.put(TRACKED_QUERY_ID_COLUMN_NAME, trackedQuery.id);
    values.put(TRACKED_QUERY_PATH_COLUMN_NAME, pathToKey(trackedQuery.querySpec.getPath()));
    values.put(TRACKED_QUERY_PARAMS_COLUMN_NAME, trackedQuery.querySpec.getParams().toJSON());
    values.put(TRACKED_QUERY_LAST_USE_COLUMN_NAME, trackedQuery.lastUse);
    values.put(TRACKED_QUERY_COMPLETE_COLUMN_NAME, trackedQuery.complete);
    values.put(TRACKED_QUERY_ACTIVE_COLUMN_NAME, trackedQuery.active);
    database.insertWithOnConflict(
        TRACKED_QUERY_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(String.format(Locale.US, "Saved new tracked query in %dms", duration));
    }
  }

  @Override
  public void deleteTrackedQuery(long trackedQueryId) {
    verifyInsideTransaction();
    String trackedQueryIdStr = String.valueOf(trackedQueryId);
    String queriesWhereClause = TRACKED_QUERY_ID_COLUMN_NAME + " = ?";
    database.delete(TRACKED_QUERY_TABLE, queriesWhereClause, new String[] {trackedQueryIdStr});

    String keysWhereClause = TRACKED_KEYS_ID_COLUMN_NAME + " = ?";
    database.delete(TRACKED_KEYS_TABLE, keysWhereClause, new String[] {trackedQueryIdStr});
  }

  @Override
  public List<TrackedQuery> loadTrackedQueries() {
    String[] columns =
        new String[] {
          TRACKED_QUERY_ID_COLUMN_NAME,
          TRACKED_QUERY_PATH_COLUMN_NAME,
          TRACKED_QUERY_PARAMS_COLUMN_NAME,
          TRACKED_QUERY_LAST_USE_COLUMN_NAME,
          TRACKED_QUERY_COMPLETE_COLUMN_NAME,
          TRACKED_QUERY_ACTIVE_COLUMN_NAME
        };
    long start = System.currentTimeMillis();
    Cursor cursor =
        database.query(
            TRACKED_QUERY_TABLE,
            columns,
            null,
            null,
            null,
            null,
            /*orderBy=*/ TRACKED_QUERY_ID_COLUMN_NAME);

    List<TrackedQuery> queries = new ArrayList<TrackedQuery>();
    try {
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        Path path = new Path(cursor.getString(1));
        String paramsStr = cursor.getString(2);
        Map<String, Object> paramsObject;
        try {
          paramsObject = JsonMapper.parseJson(paramsStr);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        QuerySpec query = QuerySpec.fromPathAndQueryObject(path, paramsObject);
        long lastUse = cursor.getLong(3);
        boolean complete = cursor.getInt(4) != 0;
        boolean active = cursor.getInt(5) != 0;
        TrackedQuery trackedQuery = new TrackedQuery(id, query, lastUse, complete, active);
        queries.add(trackedQuery);
      }
      long duration = System.currentTimeMillis() - start;
      if (logger.logsDebug()) {
        logger.debug(
            String.format(
                Locale.US, "Loaded %d tracked queries in %dms", queries.size(), duration));
      }
      return queries;
    } finally {
      cursor.close();
    }
  }

  @Override
  public void resetPreviouslyActiveTrackedQueries(long lastUse) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();

    String whereClause = TRACKED_QUERY_ACTIVE_COLUMN_NAME + " = 1";

    ContentValues values = new ContentValues();
    values.put(TRACKED_QUERY_ACTIVE_COLUMN_NAME, false);
    values.put(TRACKED_QUERY_LAST_USE_COLUMN_NAME, lastUse);

    database.updateWithOnConflict(
        TRACKED_QUERY_TABLE, values, whereClause, new String[] {}, SQLiteDatabase.CONFLICT_REPLACE);
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(String.format(Locale.US, "Reset active tracked queries in %dms", duration));
    }
  }

  @Override
  public void saveTrackedQueryKeys(long trackedQueryId, Set<ChildKey> keys) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();

    String trackedQueryIdStr = String.valueOf(trackedQueryId);
    String keysWhereClause = TRACKED_KEYS_ID_COLUMN_NAME + " = ?";
    database.delete(TRACKED_KEYS_TABLE, keysWhereClause, new String[] {trackedQueryIdStr});

    for (ChildKey addedKey : keys) {
      ContentValues values = new ContentValues();
      values.put(TRACKED_KEYS_ID_COLUMN_NAME, trackedQueryId);
      values.put(TRACKED_KEYS_KEY_COLUMN_NAME, addedKey.asString());
      database.insertWithOnConflict(
          TRACKED_KEYS_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US,
              "Set %d tracked query keys for tracked query %d in %dms",
              keys.size(),
              trackedQueryId,
              duration));
    }
  }

  @Override
  public void updateTrackedQueryKeys(
      long trackedQueryId, Set<ChildKey> added, Set<ChildKey> removed) {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    String whereClause =
        TRACKED_KEYS_ID_COLUMN_NAME + " = ? AND " + TRACKED_KEYS_KEY_COLUMN_NAME + " = ?";
    String trackedQueryIdStr = String.valueOf(trackedQueryId);
    for (ChildKey removedKey : removed) {
      database.delete(
          TRACKED_KEYS_TABLE, whereClause, new String[] {trackedQueryIdStr, removedKey.asString()});
    }
    for (ChildKey addedKey : added) {
      ContentValues values = new ContentValues();
      values.put(TRACKED_KEYS_ID_COLUMN_NAME, trackedQueryId);
      values.put(TRACKED_KEYS_KEY_COLUMN_NAME, addedKey.asString());
      database.insertWithOnConflict(
          TRACKED_KEYS_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US,
              "Updated tracked query keys (%d added, %d removed) for tracked query id %d in %dms",
              added.size(),
              removed.size(),
              trackedQueryId,
              duration));
    }
  }

  @Override
  public Set<ChildKey> loadTrackedQueryKeys(long trackedQueryId) {
    return loadTrackedQueryKeys(Collections.singleton(trackedQueryId));
  }

  @Override
  public Set<ChildKey> loadTrackedQueryKeys(Set<Long> trackedQueryIds) {
    String[] columns = new String[] {TRACKED_KEYS_KEY_COLUMN_NAME};
    long start = System.currentTimeMillis();
    String whereClause =
        TRACKED_KEYS_ID_COLUMN_NAME + " IN (" + commaSeparatedList(trackedQueryIds) + ")";
    Cursor cursor =
        database.query(
            /*distinct=*/ true,
            TRACKED_KEYS_TABLE,
            columns,
            whereClause,
            null,
            null,
            null,
            null,
            null);

    Set<ChildKey> keys = new HashSet<ChildKey>();
    try {
      while (cursor.moveToNext()) {
        String key = cursor.getString(0);
        keys.add(ChildKey.fromString(key));
      }
      long duration = System.currentTimeMillis() - start;
      if (logger.logsDebug()) {
        logger.debug(
            String.format(
                Locale.US,
                "Loaded %d tracked queries keys for tracked queries %s in %dms",
                keys.size(),
                trackedQueryIds.toString(),
                duration));
      }
      return keys;
    } finally {
      cursor.close();
    }
  }

  @Override
  public void pruneCache(Path root, PruneForest pruneForest) {
    if (!pruneForest.prunesAnything()) {
      return;
    }
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    Cursor cursor = loadNestedQuery(root, new String[] {ROW_ID_COLUMN_NAME, PATH_COLUMN_NAME});
    ImmutableTree<Long> rowIdsToPrune = new ImmutableTree<Long>(null);
    ImmutableTree<Long> rowIdsToKeep = new ImmutableTree<Long>(null);
    while (cursor.moveToNext()) {
      long rowId = cursor.getLong(0);
      Path rowPath = new Path(cursor.getString(1));
      if (!root.contains(rowPath)) {
        logger.warn(
            "We are pruning at "
                + root
                + " but we have data stored higher up at "
                + rowPath
                + ". Ignoring.");
      } else {
        Path relativePath = Path.getRelative(root, rowPath);
        if (pruneForest.shouldPruneUnkeptDescendants(relativePath)) {
          rowIdsToPrune = rowIdsToPrune.set(relativePath, rowId);
        } else if (pruneForest.shouldKeep(relativePath)) {
          rowIdsToKeep = rowIdsToKeep.set(relativePath, rowId);
        } else {
          // NOTE: This is technically a valid scenario (e.g. you ask to prune at / but only want to
          // prune 'foo' and 'bar' and ignore everything else).  But currently our pruning will
          // explicitly prune or keep everything we know about, so if we hit this it means our
          // tracked queries and the server cache are out of sync.
          logger.warn(
              "We are pruning at "
                  + root
                  + " and have data at "
                  + rowPath
                  + " that isn't marked for pruning or keeping. Ignoring.");
        }
      }
    }

    int prunedCount = 0, resavedCount = 0;
    if (!rowIdsToPrune.isEmpty()) {
      List<Pair<Path, Node>> rowsToResave = new ArrayList<Pair<Path, Node>>();
      pruneTreeRecursive(
          root, Path.getEmptyPath(), rowIdsToPrune, rowIdsToKeep, pruneForest, rowsToResave);

      Collection<Long> rowIdsToDelete = rowIdsToPrune.values();
      String whereClause = "rowid IN (" + commaSeparatedList(rowIdsToDelete) + ")";
      database.delete(SERVER_CACHE_TABLE, whereClause, null);

      for (Pair<Path, Node> node : rowsToResave) {
        this.saveNested(root.child(node.getFirst()), node.getSecond());
      }

      prunedCount = rowIdsToDelete.size();
      resavedCount = rowsToResave.size();
    }
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US,
              "Pruned %d rows with %d nodes resaved in %dms",
              prunedCount,
              resavedCount,
              duration));
    }
  }

  private void pruneTreeRecursive(
      final Path pruneRoot,
      final Path relativePath,
      final ImmutableTree<Long> rowIdsToPrune,
      final ImmutableTree<Long> rowIdsToKeep,
      final PruneForest pruneForest,
      final List<Pair<Path, Node>> rowsToResaveAccumulator) {

    if (rowIdsToPrune.getValue() != null) {
      // There is a row at this path, figure out if we have to do some resaving
      int nodesToResave =
          pruneForest.foldKeptNodes(
              0,
              new ImmutableTree.TreeVisitor<Void, Integer>() {
                @Override
                public Integer onNodeValue(Path keepPath, Void ignore, Integer nodesToResave) {
                  // Only need to resave if there doesn't exist a row at that path
                  return (rowIdsToKeep.get(keepPath) == null) ? nodesToResave + 1 : nodesToResave;
                }
              });
      if (nodesToResave > 0) {
        Path absolutePath = pruneRoot.child(relativePath);
        if (logger.logsDebug()) {
          logger.debug(
              String.format(
                  Locale.US,
                  "Need to rewrite %d nodes below path %s",
                  nodesToResave,
                  absolutePath));
        }
        final Node currentNode = loadNested(absolutePath);
        pruneForest.foldKeptNodes(
            null,
            new ImmutableTree.TreeVisitor<Void, Void>() {
              @Override
              public Void onNodeValue(Path keepPath, Void ignore, Void ignore2) {
                // Only need to resave if there doesn't exist a row at that path
                if (rowIdsToKeep.get(keepPath) == null) {
                  rowsToResaveAccumulator.add(
                      new Pair<Path, Node>(
                          relativePath.child(keepPath), currentNode.getChild(keepPath)));
                }
                return null;
              }
            });
      }
    } else {
      // There is no row at this path, iterate over all children
      for (Map.Entry<ChildKey, ImmutableTree<Long>> entry : rowIdsToPrune.getChildren()) {
        ChildKey childKey = entry.getKey();
        PruneForest childPruneForest = pruneForest.child(entry.getKey());
        pruneTreeRecursive(
            pruneRoot,
            relativePath.child(childKey),
            entry.getValue(),
            rowIdsToKeep.getChild(childKey),
            childPruneForest,
            rowsToResaveAccumulator);
      }
    }
  }

  @Override
  public void removeAllUserWrites() {
    verifyInsideTransaction();
    long start = System.currentTimeMillis();
    int count = database.delete(WRITES_TABLE, null, null);
    long duration = System.currentTimeMillis() - start;
    if (logger.logsDebug()) {
      logger.debug(String.format(Locale.US, "Deleted %d (all) write(s) in %dms", count, duration));
    }
  }

  public void purgeCache() {
    verifyInsideTransaction();
    database.delete(SERVER_CACHE_TABLE, null, null);
    database.delete(WRITES_TABLE, null, null);
    database.delete(TRACKED_QUERY_TABLE, null, null);
    database.delete(TRACKED_KEYS_TABLE, null, null);
  }

  @Override
  public void beginTransaction() {
    hardAssert(
        !insideTransaction,
        "runInTransaction called when an existing transaction is already in progress.");
    if (logger.logsDebug()) {
      logger.debug("Starting transaction.");
    }
    database.beginTransaction();
    insideTransaction = true;
    transactionStart = System.currentTimeMillis();
  }

  @Override
  public void endTransaction() {
    database.endTransaction();
    insideTransaction = false;
    long elapsed = System.currentTimeMillis() - transactionStart;
    if (logger.logsDebug()) {
      logger.debug(String.format(Locale.US, "Transaction completed. Elapsed: %dms", elapsed));
    }
  }

  @Override
  public void setTransactionSuccessful() {
    database.setTransactionSuccessful();
  }

  @Override
  public void close() {
    database.close();
  }

  private SQLiteDatabase openDatabase(Context context, String cacheId) {
    PersistentCacheOpenHelper helper = new PersistentCacheOpenHelper(context, cacheId);

    try {
      // In the future we might want to consider turning off fsync writes for SQL. While this
      // comes with a danger of corruption, corruptions on Android should be pretty rare and
      // we can survive cases where the cache has been deleted. The performance gains should
      // be measured and evaluated however.
      SQLiteDatabase database = helper.getWritableDatabase();

      // Set locking mode to EXCLUSIVE since we don't support multi-process apps using
      // persistence.
      database.rawQuery("PRAGMA locking_mode = EXCLUSIVE", null).close();

      // Apparently the EXCLUSIVE lock is acquired lazily (on first read/write) but then held
      // indefinitely. So do a dummy exclusive transaction to actually acquire the lock.
      database.beginTransaction();
      database.endTransaction();

      return database;
    } catch (SQLiteException e) {
      // NOTE: Ideally we'd catch SQLiteDatabaseLockedException, but that requires API Level
      // 11 and we support 9 so we can't.
      if (e instanceof SQLiteDatabaseLockedException) {
        String msg =
            "Failed to gain exclusive lock to Firebase Database's offline"
                + " persistence. This generally means you are using Firebase Database from"
                + " multiple processes in your app. Keep in mind that multi-process Android"
                + " apps execute the code in your Application class in all processes, so you"
                + " may need to avoid initializing FirebaseDatabase in your Application class."
                + " If you are intentionally using Firebase Database from multiple processes,"
                + " you can only enable offline persistence (i.e. call"
                + " setPersistenceEnabled(true)) in one of them.";
        throw new DatabaseException(msg, e);
      } else {
        throw e;
      }
    }
  }

  private void verifyInsideTransaction() {
    hardAssert(this.insideTransaction, "Transaction expected to already be in progress.");
  }

  /**
   * This method saves a node into the database. If a children node is above the split threshold,
   * every child will be saved separately. The child might be split again. This method returns the
   * number of rows saved to the database.
   *
   * @param path The path to save the node at
   * @param node The node to save
   * @return The number of saved database rows
   */
  private int saveNested(Path path, Node node) {
    long estimatedSize = NodeSizeEstimator.estimateSerializedNodeSize(node);
    if (node instanceof ChildrenNode && estimatedSize > CHILDREN_NODE_SPLIT_SIZE_THRESHOLD) {
      if (logger.logsDebug()) {
        logger.debug(
            String.format(
                Locale.US,
                "Node estimated serialized size at path %s of %d bytes exceeds limit of %d bytes. "
                    + "Splitting up.",
                path,
                estimatedSize,
                CHILDREN_NODE_SPLIT_SIZE_THRESHOLD));
      }
      // split up the children node into multiple nodes
      int sum = 0;
      for (NamedNode child : node) {
        sum += saveNested(path.child(child.getName()), child.getNode());
      }
      if (!node.getPriority().isEmpty()) {
        saveNode(path.child(ChildKey.getPriorityKey()), node.getPriority());
        sum++;
      }

      // Need to save an empty node here to make sure we still supersede anything written by parent
      // paths.
      saveNode(path, EmptyNode.Empty());
      sum++;

      return sum;
    } else {
      saveNode(path, node);
      return 1;
    }
  }

  private String partKey(Path path, int i) {
    return pathToKey(path) + String.format(Locale.US, PART_KEY_FORMAT, i);
  }

  private void saveNode(Path path, Node node) {
    byte[] serialized = serializeObject(node.getValue(true));
    if (serialized.length >= ROW_SPLIT_SIZE) {
      List<byte[]> parts = splitBytes(serialized, ROW_SPLIT_SIZE);
      if (logger.logsDebug()) {
        logger.debug("Saving huge leaf node with " + parts.size() + " parts.");
      }
      for (int i = 0; i < parts.size(); i++) {
        ContentValues values = new ContentValues();
        values.put(PATH_COLUMN_NAME, partKey(path, i));
        values.put(VALUE_COLUMN_NAME, parts.get(i));
        database.insertWithOnConflict(
            SERVER_CACHE_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
      }
    } else {
      ContentValues values = new ContentValues();
      values.put(PATH_COLUMN_NAME, pathToKey(path));
      values.put(VALUE_COLUMN_NAME, serialized);
      database.insertWithOnConflict(
          SERVER_CACHE_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
  }

  /**
   * Loads a node at a path. This method reads all rows that could contribute to the current state
   * of the node and combines them. It has no knowledge of whether the data is "complete" or not.
   *
   * @param path The path at which to load the node.
   * @return The node that was loaded.
   */
  private Node loadNested(Path path) {
    List<String> pathStrings = new ArrayList<String>();
    List<byte[]> payloads = new ArrayList<byte[]>();

    long queryStart = System.currentTimeMillis();
    Cursor cursor = loadNestedQuery(path, new String[] {PATH_COLUMN_NAME, VALUE_COLUMN_NAME});
    long queryDuration = System.currentTimeMillis() - queryStart;
    long loadingStart = System.currentTimeMillis();
    try {
      while (cursor.moveToNext()) {
        pathStrings.add(cursor.getString(0));
        payloads.add(cursor.getBlob(1));
      }
    } finally {
      cursor.close();
    }
    long loadingDuration = System.currentTimeMillis() - loadingStart;
    long serializingStart = System.currentTimeMillis();

    Node node = EmptyNode.Empty();
    boolean sawDescendant = false;
    Map<Path, Node> priorities = new HashMap<Path, Node>();
    for (int i = 0; i < payloads.size(); i++) {
      Node savedNode;
      Path savedPath;
      if (pathStrings.get(i).endsWith(FIRST_PART_KEY)) {
        // This is a multi-part leaf node, load all the parts and advance the for loop counter.
        // Parts are guaranteed to be in order because the query is ordered by the path key.
        String pathString = pathStrings.get(i);
        savedPath =
            new Path(pathString.substring(0, pathString.length() - FIRST_PART_KEY.length()));
        int splitNodeRunLength = splitNodeRunLength(savedPath, pathStrings, i);
        if (logger.logsDebug()) {
          logger.debug("Loading split node with " + splitNodeRunLength + " parts.");
        }
        savedNode = deserializeNode(joinBytes(payloads.subList(i, i + splitNodeRunLength)));
        // advance to last element of split node
        i = i + splitNodeRunLength - 1;
      } else {
        savedNode = deserializeNode(payloads.get(i));
        savedPath = new Path(pathStrings.get(i));
      }
      if (savedPath.getBack() != null && savedPath.getBack().isPriorityChildName()) {
        // Apply priorites at the end. At that point deeper nodes should have updated any empty
        // nodes
        priorities.put(savedPath, savedNode);
      } else if (savedPath.contains(path)) {
        hardAssert(!sawDescendant, "Descendants of path must come after ancestors.");
        node = savedNode.getChild(Path.getRelative(savedPath, path));
      } else if (path.contains(savedPath)) {
        sawDescendant = true;
        Path childPath = Path.getRelative(path, savedPath);
        node = node.updateChild(childPath, savedNode);
      } else {
        throw new IllegalStateException(
            String.format("Loading an unrelated row with path %s for %s", savedPath, path));
      }
    }

    // Apply priorities
    for (Map.Entry<Path, Node> entry : priorities.entrySet()) {
      Path priorityPath = entry.getKey();
      node = node.updateChild(Path.getRelative(path, priorityPath), entry.getValue());
    }

    long serializeDuration = System.currentTimeMillis() - serializingStart;
    long duration = System.currentTimeMillis() - queryStart;
    if (logger.logsDebug()) {
      logger.debug(
          String.format(
              Locale.US,
              "Loaded a total of %d rows for a total of %d nodes at %s in %dms "
                  + "(Query: %dms, Loading: %dms, Serializing: %dms)",
              payloads.size(),
              NodeSizeEstimator.nodeCount(node),
              path,
              duration,
              queryDuration,
              loadingDuration,
              serializeDuration));
    }
    return node;
  }

  private int splitNodeRunLength(Path path, List<String> pathStrings, int startPosition) {
    int endPosition = startPosition + 1;
    String pathPrefix = pathToKey(path);
    if (!pathStrings.get(startPosition).startsWith(pathPrefix)) {
      throw new IllegalStateException("Extracting split nodes needs to start with path prefix");
    }
    while (endPosition < pathStrings.size()
        && pathStrings.get(endPosition).equals(partKey(path, endPosition - startPosition))) {
      endPosition++;
    }
    if (endPosition < pathStrings.size()
        && pathStrings.get(endPosition).startsWith(pathPrefix + PART_KEY_PREFIX)) {
      throw new IllegalStateException("Run did not finish with all parts");
    }
    return (endPosition - startPosition);
  }

  private Cursor loadNestedQuery(Path path, String[] columns) {
    String pathPrefixStart = pathToKey(path);
    String pathPrefixEnd = pathPrefixStartToPrefixEnd(pathPrefixStart);

    String[] arguments = new String[path.size() + 3];
    String whereClause = buildAncestorWhereClause(path, arguments);
    whereClause += " OR (" + PATH_COLUMN_NAME + " > ? AND " + PATH_COLUMN_NAME + " < ?)";
    arguments[path.size() + 1] = pathPrefixStart;
    arguments[path.size() + 2] = pathPrefixEnd;
    String orderBy = PATH_COLUMN_NAME;

    return database.query(SERVER_CACHE_TABLE, columns, whereClause, arguments, null, null, orderBy);
  }

  private static String pathToKey(Path path) {
    if (path.isEmpty()) {
      return "/";
    } else {
      return path.toString() + "/";
    }
  }

  private static String pathPrefixStartToPrefixEnd(String prefix) {
    hardAssert(prefix.endsWith("/"), "Path keys must end with a '/'");
    return prefix.substring(0, prefix.length() - 1) + (char) ('/' + 1);
  }

  private static String buildAncestorWhereClause(Path path, String[] arguments) {
    hardAssert(arguments.length >= path.size() + 1);
    int count = 0;
    StringBuilder whereClause = new StringBuilder("(");
    while (!path.isEmpty()) {
      whereClause.append(PATH_COLUMN_NAME);
      whereClause.append(" = ? OR ");
      arguments[count] = pathToKey(path);
      path = path.getParent();
      count++;
    }
    whereClause.append(PATH_COLUMN_NAME);
    whereClause.append(" = ?)");
    arguments[count] = pathToKey(Path.getEmptyPath());
    return whereClause.toString();
  }

  private int removeNested(String table, Path path) {
    String pathPrefixQuery = PATH_COLUMN_NAME + " >= ? AND " + PATH_COLUMN_NAME + " < ?";
    String pathPrefixStart = pathToKey(path);
    String pathPrefixEnd = pathPrefixStartToPrefixEnd(pathPrefixStart);
    return database.delete(table, pathPrefixQuery, new String[] {pathPrefixStart, pathPrefixEnd});
  }

  private static List<byte[]> splitBytes(byte[] bytes, int size) {
    int parts = ((bytes.length - 1) / size) + 1;
    List<byte[]> partList = new ArrayList<byte[]>(parts);
    for (int i = 0; i < parts; i++) {
      int length = Math.min(size, bytes.length - (i * size));
      byte[] part = new byte[length];
      System.arraycopy(bytes, i * size, part, 0, length);
      partList.add(part);
    }
    return partList;
  }

  private byte[] joinBytes(List<byte[]> payloads) {
    int totalSize = 0;
    for (byte[] payload : payloads) {
      totalSize += payload.length;
    }
    byte[] buffer = new byte[totalSize];
    int currentBytePosition = 0;
    for (byte[] payload : payloads) {
      System.arraycopy(payload, 0, buffer, currentBytePosition, payload.length);
      currentBytePosition += payload.length;
    }
    return buffer;
  }

  private byte[] serializeObject(Object object) {
    try {
      return JsonMapper.serializeJsonValue(object).getBytes(UTF8_CHARSET);
    } catch (IOException e) {
      throw new RuntimeException("Could not serialize leaf node", e);
    }
  }

  private Node deserializeNode(byte[] value) {
    try {
      Object o = JsonMapper.parseJsonValue(new String(value, UTF8_CHARSET));
      return NodeUtilities.NodeFromJSON(o);
    } catch (IOException e) {
      String stringValue = new String(value, UTF8_CHARSET);
      throw new RuntimeException("Could not deserialize node: " + stringValue, e);
    }
  }

  private String commaSeparatedList(Collection<Long> items) {
    StringBuilder list = new StringBuilder();
    boolean first = true;
    for (long item : items) {
      if (!first) {
        list.append(",");
      }
      first = false;
      list.append(item);
    }
    return list.toString();
  }
}
