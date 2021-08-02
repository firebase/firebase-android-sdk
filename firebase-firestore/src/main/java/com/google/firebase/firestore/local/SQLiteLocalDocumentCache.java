package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class SQLiteLocalDocumentCache implements LocalDocumentCache {
  private final SQLitePersistence db;
  private final String uid;
  private final LocalSerializer serializer;

  SQLiteLocalDocumentCache(SQLitePersistence persistence, User user, LocalSerializer serializer) {
    this.db = persistence;
    this.uid = user.isAuthenticated() ? user.getUid() : "";
    this.serializer = serializer;
  }

  @Override
  public void add(MutableDocument doc) {
    if (doc == null) return;
    String path = EncodedPath.encode(doc.getKey().getPath());
    int mutationFlag = 0;
    if (doc.hasLocalMutations()) {
      mutationFlag |= 1;
    }
    if (doc.hasCommittedMutations()) {
      mutationFlag |= 2;
    }
    // hardAssert(mutationFlag != 0, "Adding document with no mutations to local doc cache.");
    db.execute(
        "INSERT OR REPLACE INTO local_documents (path, uid, content, mutation_flag) VALUES (?, ?, ?, ?)",
        path,
        uid,
        serializer.encodeMaybeDocument(doc).toByteArray(),
        mutationFlag);
  }

  @Override
  public void remove(DocumentKey key) {
    String path = EncodedPath.encode(key.getPath());
    db.execute("DELETE FROM local_documents WHERE path = ?", path);
  }

  @Override
  public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> keys) {
    List<Object> args = new ArrayList<>();
    for (DocumentKey key : keys) {
      args.add(EncodedPath.encode(key.getPath()));
    }

    Map<DocumentKey, MutableDocument> results = new HashMap<>();

    SQLitePersistence.LongQuery longQuery =
        new SQLitePersistence.LongQuery(
            db,
            "SELECT content, mutation_flag FROM local_documents " + "WHERE path IN (",
            args,
            ") AND uid = \"" + uid + "\" ORDER BY path");

    while (longQuery.hasMoreSubqueries()) {
      longQuery
          .performNextSubquery()
          .forEach(
              row -> {
                MutableDocument decoded = decodeMaybeDocument(row.getBlob(0));
                if (decoded != null) {
                  int mutationFlag = row.getInt(1);
                  if (mutationFlag == 1) {
                    results.put(decoded.getKey(), decoded.setHasLocalMutations());
                  } else if (mutationFlag == 2) {
                    results.put(decoded.getKey(), decoded.setHasCommittedMutations());
                  }
                  results.put(decoded.getKey(), decoded);
                }else {
                  results.put(decoded.getKey(), null);
                }
              });
    }

    return results;
  }

  @Override
  public MutableDocument get(DocumentKey key) {
    String path = EncodedPath.encode(key.getPath());

    MutableDocument document =
        db.query("SELECT content, mutation_flag FROM local_documents WHERE path = ? AND uid = ?")
            .binding(path, uid)
            .firstValue(
                row -> {
                  MutableDocument doc = decodeMaybeDocument(row.getBlob(0));
                  int mutationFlag = row.getInt(1);
                  if (mutationFlag == 1) {
                    return doc.setHasLocalMutations();
                  } else if (mutationFlag == 2) {
                    return doc.setHasCommittedMutations();
                  }
                  return doc;
                });
    return document;
  }

  private MutableDocument decodeMaybeDocument(byte[] bytes) {
    try {
      return serializer.decodeMaybeDocument(
          com.google.firebase.firestore.proto.MaybeDocument.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw fail("MaybeDocument failed to parse: %s", e);
    }
  }

  @Override
  public ImmutableSortedMap<DocumentKey, MutableDocument> getAllDocumentsMatchingQuery(
      Query query) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");

    // Use the query path as a prefix for testing if a document matches the query.
    ResourcePath prefix = query.getPath();
    int immediateChildrenPathLength = prefix.length() + 1;

    String prefixPath = EncodedPath.encode(prefix);
    String prefixSuccessorPath = EncodedPath.prefixSuccessor(prefixPath);

    BackgroundQueue backgroundQueue = new BackgroundQueue();

    ImmutableSortedMap<DocumentKey, MutableDocument>[] matchingDocuments =
        (ImmutableSortedMap<DocumentKey, MutableDocument>[])
            new ImmutableSortedMap[] {DocumentCollections.emptyMutableDocumentMap()};

    SQLitePersistence.Query sqlQuery;
    sqlQuery =
        db.query(
                "SELECT path, content, mutation_flag FROM local_documents WHERE path >= ? AND path < ? AND uid = ?")
            .binding(prefixPath, prefixSuccessorPath, uid);

    sqlQuery.forEach(
        row -> {
          // TODO: Actually implement a single-collection query
          //
          // The query is actually returning any path that starts with the query path prefix
          // which may include documents in subcollections. For example, a query on 'rooms'
          // will return rooms/abc/messages/xyx but we shouldn't match it. Fix this by
          // discarding rows with document keys more than one segment longer than the query
          // path.
          ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
          if (path.length() != immediateChildrenPathLength) {
            return;
          }

          byte[] rawDocument = row.getBlob(1);
          int mutationFlag = row.getInt(2);

          // Since scheduling background tasks incurs overhead, we only dispatch to a
          // background thread if there are still some documents remaining.
          Executor executor = row.isLast() ? Executors.DIRECT_EXECUTOR : backgroundQueue;
          executor.execute(
              () -> {
                MutableDocument document = decodeMaybeDocument(rawDocument);
                if (document.isFoundDocument() && query.matches(document)) {
                  if (mutationFlag == 1) {
                    document = document.setHasLocalMutations();
                  } else if (mutationFlag == 2) {
                    document = document.setHasCommittedMutations();
                  }
                  synchronized (SQLiteLocalDocumentCache.this) {
                    matchingDocuments[0] = matchingDocuments[0].insert(document.getKey(), document);
                  }
                }
              });
        });

    try {
      backgroundQueue.drain();
    } catch (InterruptedException e) {
      fail("Interrupted while deserializing documents", e);
    }

    return matchingDocuments[0];
  }
}
