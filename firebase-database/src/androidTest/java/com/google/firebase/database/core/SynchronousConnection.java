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

package com.google.firebase.database.core;

import static com.google.firebase.database.IntegrationTestHelpers.newFrozenTestConfig;
import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.connection.PersistentConnectionImpl;
import com.google.firebase.database.connection.RequestResultCallback;
import com.google.firebase.database.core.utilities.DefaultRunLoop;
import com.google.firebase.database.core.utilities.Pair;
import com.google.firebase.database.core.utilities.ParsedUrl;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.CompoundHash;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.RangeMerge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

public class SynchronousConnection implements PersistentConnection.Delegate {

  private final Context context;
  private final Path path;
  private final PersistentConnection connection;
  private final Semaphore connectSemaphore;
  private Pair<Path, List<RangeMerge>> lastRangeMerge;
  private boolean destroyed = false;

  public SynchronousConnection(String host) {
    ParsedUrl parsed = Utilities.parseUrl(host);
    this.path = parsed.path;
    context = newFrozenTestConfig();
    ScheduledExecutorService executorService =
        ((DefaultRunLoop) context.getRunLoop()).getExecutorService();
    RepoInfo repoInfo = parsed.repoInfo;
    HostInfo hostInfo = new HostInfo(repoInfo.host, repoInfo.namespace, repoInfo.secure);

    connection = new PersistentConnectionImpl(context.getConnectionContext(), hostInfo, this);
    this.connectSemaphore = new Semaphore(0);
  }

  public void connect() {
    context
        .getRunLoop()
        .scheduleNow(
            new Runnable() {
              @Override
              public void run() {
                connection.initialize();
              }
            });
    try {
      this.connectSemaphore.acquire();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void destroy() {
    destroyed = true;
    context
        .getRunLoop()
        .scheduleNow(
            new Runnable() {
              @Override
              public void run() {
                connection.shutdown();
                context.stop();
              }
            });
  }

  public void setValue(Path path, final Node node, boolean wait) {
    hardAssert(!destroyed);
    final Semaphore semaphore = wait ? new Semaphore(0) : null;
    final Path fullPath = this.path.child(path);
    final DatabaseError[] errorBox = new DatabaseError[1];
    context
        .getRunLoop()
        .scheduleNow(
            new Runnable() {
              @Override
              public void run() {
                connection.put(
                    fullPath.asList(),
                    node.getValue(true),
                    new RequestResultCallback() {
                      @Override
                      public void onRequestResult(String optErrorCode, String optErrorMessage) {
                        errorBox[0] =
                            (optErrorCode == null)
                                ? null
                                : DatabaseError.fromStatus(optErrorCode, optErrorMessage);
                        if (semaphore != null) {
                          semaphore.release();
                        }
                      }
                    });
              }
            });
    if (semaphore != null) {
      try {
        semaphore.acquire();
        if (errorBox[0] != null) {
          throw errorBox[0].toException();
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public List<RangeMerge> listenWithHash(
      final Path path, final CompoundHash hash, final String simpleHash) {
    hardAssert(!destroyed);
    lastRangeMerge = null;
    final Semaphore semaphore = new Semaphore(0);
    final Path fullPath = this.path.child(path);
    final QuerySpec spec = QuerySpec.defaultQueryAtPath(fullPath);
    context
        .getRunLoop()
        .scheduleNow(
            new Runnable() {
              @Override
              public void run() {
                connection.listen(
                    spec.getPath().asList(),
                    spec.getParams().getWireProtocolParams(),
                    new ListenHashProvider() {
                      @Override
                      public com.google.firebase.database.connection.CompoundHash
                          getCompoundHash() {
                        List<Path> pathPosts = hash.getPosts();
                        List<List<String>> posts = new ArrayList<List<String>>(pathPosts.size());
                        for (Path path : pathPosts) {
                          posts.add(path.asList());
                        }
                        return new com.google.firebase.database.connection.CompoundHash(
                            posts, hash.getHashes());
                      }

                      @Override
                      public String getSimpleHash() {
                        return simpleHash;
                      }

                      @Override
                      public boolean shouldIncludeCompoundHash() {
                        return true;
                      }
                    },
                    /*tag=*/ null,
                    new RequestResultCallback() {
                      @Override
                      public void onRequestResult(String optErrorCode, String optErrorMessage) {
                        semaphore.release();
                      }
                    });
              }
            });
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    context
        .getRunLoop()
        .scheduleNow(
            new Runnable() {
              @Override
              public void run() {
                connection.unlisten(
                    spec.getPath().asList(), spec.getParams().getWireProtocolParams());
              }
            });
    if (lastRangeMerge == null) {
      return null;
    } else {
      if (!lastRangeMerge.getFirst().equals(path)) {
        throw new InternalError("Unexpected path for range merge: " + lastRangeMerge);
      }
      return lastRangeMerge.getSecond();
    }
  }

  @Override
  public void onDataUpdate(List<String> path, Object message, boolean isMerge, Long optTag) {
    // ignore
  }

  @Override
  public void onRangeMergeUpdate(
      List<String> path,
      List<com.google.firebase.database.connection.RangeMerge> merges,
      Long optTag) {
    List<RangeMerge> rangeMerges = new ArrayList<>();
    for (com.google.firebase.database.connection.RangeMerge merge : merges) {
      rangeMerges.add(new RangeMerge(merge));
    }

    this.lastRangeMerge = new Pair<Path, List<RangeMerge>>(new Path(path), rangeMerges);
  }

  @Override
  public void onConnect() {
    this.connectSemaphore.release();
  }

  @Override
  public void onDisconnect() {
    if (!destroyed) {
      throw new IllegalStateException("Unexpected disconnect!");
    }
  }

  @Override
  public void onAuthStatus(boolean authOk) {
    // ignore
  }

  @Override
  public void onServerInfoUpdate(Map<String, Object> updates) {
    // Ignore
  }
}
