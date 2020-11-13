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

package com.google.firebase.database.connection;

import static com.google.firebase.database.connection.ConnectionUtils.hardAssert;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.database.connection.util.RetryHelper;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.util.GAuthToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PersistentConnectionImpl implements Connection.Delegate, PersistentConnection {

  private interface ConnectionRequestCallback {
    void onResponse(Map<String, Object> response);
  }

  private static class QuerySpec {
    private final List<String> path;
    private final Map<String, Object> queryParams;

    public QuerySpec(List<String> path, Map<String, Object> queryParams) {
      this.path = path;
      this.queryParams = queryParams;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof QuerySpec)) {
        return false;
      }

      QuerySpec that = (QuerySpec) o;

      if (!path.equals(that.path)) {
        return false;
      }
      return queryParams.equals(that.queryParams);
    }

    @Override
    public int hashCode() {
      int result = path.hashCode();
      result = 31 * result + queryParams.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return ConnectionUtils.pathToString(this.path) + " (params: " + queryParams + ")";
    }
  }

  private static class OutstandingListen {
    private final RequestResultCallback resultCallback;
    private final QuerySpec query;
    private final ListenHashProvider hashFunction;
    private final Long tag;

    private OutstandingListen(
        RequestResultCallback callback,
        QuerySpec query,
        Long tag,
        ListenHashProvider hashFunction) {
      this.resultCallback = callback;
      this.query = query;
      this.hashFunction = hashFunction;
      this.tag = tag;
    }

    public QuerySpec getQuery() {
      return query;
    }

    public Long getTag() {
      return this.tag;
    }

    public ListenHashProvider getHashFunction() {
      return this.hashFunction;
    }

    @Override
    public String toString() {
      return query.toString() + " (Tag: " + this.tag + ")";
    }
  }

  private static class OutstandingGet {
    private final Map<String, Object> request;
    private final ConnectionRequestCallback onComplete;
    private boolean sent;

    private OutstandingGet(
        String action, Map<String, Object> request, ConnectionRequestCallback onComplete) {
      this.request = request;
      this.onComplete = onComplete;
      this.sent = false;
    }

    private ConnectionRequestCallback getOnComplete() {
      return onComplete;
    }

    private Map<String, Object> getRequest() {
      return request;
    }

    /**
     * Mark this OutstandingGet as sent. Essentially compare-and-set on the `sent` member.
     *
     * @return true if the OustandingGet wasn't already sent, false if it was.
     */
    private boolean markSent() {
      if (sent) {
        return false;
      }
      sent = true;
      return true;
    }
  }

  private static class OutstandingPut {
    private String action;
    private Map<String, Object> request;
    private RequestResultCallback onComplete;
    private boolean sent;

    private OutstandingPut(
        String action, Map<String, Object> request, RequestResultCallback onComplete) {
      this.action = action;
      this.request = request;
      this.onComplete = onComplete;
    }

    public String getAction() {
      return action;
    }

    public Map<String, Object> getRequest() {
      return request;
    }

    public RequestResultCallback getOnComplete() {
      return onComplete;
    }

    public void markSent() {
      this.sent = true;
    }

    public boolean wasSent() {
      return this.sent;
    }
  }

  private static class OutstandingDisconnect {
    private final String action;
    private final List<String> path;
    private final Object data;
    private final RequestResultCallback onComplete;

    private OutstandingDisconnect(
        String action, List<String> path, Object data, RequestResultCallback onComplete) {
      this.action = action;
      this.path = path;
      this.data = data;
      this.onComplete = onComplete;
    }

    public String getAction() {
      return action;
    }

    public List<String> getPath() {
      return path;
    }

    public Object getData() {
      return data;
    }

    public RequestResultCallback getOnComplete() {
      return onComplete;
    }
  }

  private enum ConnectionState {
    Disconnected,
    GettingToken,
    Connecting,
    Authenticating,
    Connected
  }

  private static final String REQUEST_ERROR = "error";
  private static final String REQUEST_QUERIES = "q";
  private static final String REQUEST_TAG = "t";
  private static final String REQUEST_STATUS = "s";
  private static final String REQUEST_PATH = "p";
  private static final String REQUEST_NUMBER = "r";
  private static final String REQUEST_PAYLOAD = "b";
  private static final String REQUEST_COUNTERS = "c";
  private static final String REQUEST_DATA_PAYLOAD = "d";
  private static final String REQUEST_DATA_HASH = "h";
  private static final String REQUEST_COMPOUND_HASH = "ch";
  private static final String REQUEST_COMPOUND_HASH_PATHS = "ps";
  private static final String REQUEST_COMPOUND_HASH_HASHES = "hs";
  private static final String REQUEST_CREDENTIAL = "cred";
  private static final String REQUEST_AUTHVAR = "authvar";
  private static final String REQUEST_ACTION = "a";
  private static final String REQUEST_ACTION_STATS = "s";
  private static final String REQUEST_ACTION_QUERY = "q";
  private static final String REQUEST_ACTION_GET = "g";
  private static final String REQUEST_ACTION_PUT = "p";
  private static final String REQUEST_ACTION_MERGE = "m";
  private static final String REQUEST_ACTION_QUERY_UNLISTEN = "n";
  private static final String REQUEST_ACTION_ONDISCONNECT_PUT = "o";
  private static final String REQUEST_ACTION_ONDISCONNECT_MERGE = "om";
  private static final String REQUEST_ACTION_ONDISCONNECT_CANCEL = "oc";
  private static final String REQUEST_ACTION_AUTH = "auth";
  private static final String REQUEST_ACTION_GAUTH = "gauth";
  private static final String REQUEST_ACTION_UNAUTH = "unauth";
  private static final String RESPONSE_FOR_REQUEST = "b";
  private static final String SERVER_ASYNC_ACTION = "a";
  private static final String SERVER_ASYNC_PAYLOAD = "b";
  private static final String SERVER_ASYNC_DATA_UPDATE = "d";
  private static final String SERVER_ASYNC_DATA_MERGE = "m";
  private static final String SERVER_ASYNC_DATA_RANGE_MERGE = "rm";
  private static final String SERVER_ASYNC_AUTH_REVOKED = "ac";
  private static final String SERVER_ASYNC_LISTEN_CANCELLED = "c";
  private static final String SERVER_ASYNC_SECURITY_DEBUG = "sd";
  private static final String SERVER_DATA_UPDATE_PATH = "p";
  private static final String SERVER_DATA_UPDATE_BODY = "d";
  private static final String SERVER_DATA_START_PATH = "s";
  private static final String SERVER_DATA_END_PATH = "e";
  private static final String SERVER_DATA_RANGE_MERGE = "m";
  private static final String SERVER_DATA_TAG = "t";
  private static final String SERVER_DATA_WARNINGS = "w";
  private static final String SERVER_RESPONSE_DATA = "d";

  /** Delay after which a established connection is considered successful */
  private static final long SUCCESSFUL_CONNECTION_ESTABLISHED_DELAY = 30 * 1000;

  private static final long IDLE_TIMEOUT = 60 * 1000;
  private static final long GET_CONNECT_TIMEOUT = 3 * 1000;

  /** If auth fails repeatedly, we'll assume something is wrong and log a warning / back off. */
  private static final long INVALID_AUTH_TOKEN_THRESHOLD = 3;

  private static final String SERVER_KILL_INTERRUPT_REASON = "server_kill";
  private static final String IDLE_INTERRUPT_REASON = "connection_idle";
  private static final String TOKEN_REFRESH_INTERRUPT_REASON = "token_refresh";

  private static long connectionIds = 0;

  private final Delegate delegate;
  private final HostInfo hostInfo;
  private String cachedHost;
  private HashSet<String> interruptReasons = new HashSet<String>();
  private boolean firstConnection = true;
  private long lastConnectionEstablishedTime;
  private Connection realtime;
  private ConnectionState connectionState = ConnectionState.Disconnected;
  private long writeCounter = 0;
  private long readCounter = 0;
  private long requestCounter = 0;
  private Map<Long, ConnectionRequestCallback> requestCBHash;

  private List<OutstandingDisconnect> onDisconnectRequestQueue;
  private Map<Long, OutstandingPut> outstandingPuts;
  private Map<Long, OutstandingGet> outstandingGets;

  private Map<QuerySpec, OutstandingListen> listens;
  private String authToken;
  private boolean forceAuthTokenRefresh;
  private final ConnectionContext context;
  private final ConnectionAuthTokenProvider authTokenProvider;
  private final ScheduledExecutorService executorService;
  private final LogWrapper logger;
  private final RetryHelper retryHelper;
  private String lastSessionId;
  /** Counter to check whether the callback is for the last getToken call */
  private long currentGetTokenAttempt = 0;

  private int invalidAuthTokenCount = 0;

  private ScheduledFuture<?> inactivityTimer = null;
  private long lastWriteTimestamp;
  private boolean hasOnDisconnects;

  public PersistentConnectionImpl(
      ConnectionContext context, HostInfo info, final Delegate delegate) {
    this.delegate = delegate;
    this.context = context;
    this.executorService = context.getExecutorService();
    this.authTokenProvider = context.getAuthTokenProvider();
    this.hostInfo = info;
    this.listens = new HashMap<QuerySpec, OutstandingListen>();
    this.requestCBHash = new HashMap<Long, ConnectionRequestCallback>();
    this.outstandingPuts = new HashMap<Long, OutstandingPut>();
    this.outstandingGets = new ConcurrentHashMap<Long, OutstandingGet>();
    this.onDisconnectRequestQueue = new ArrayList<OutstandingDisconnect>();
    this.retryHelper =
        new RetryHelper.Builder(this.executorService, context.getLogger(), "ConnectionRetryHelper")
            .withMinDelayAfterFailure(1000)
            .withRetryExponent(1.3)
            .withMaxDelay(30 * 1000)
            .withJitterFactor(0.7)
            .build();

    long connId = connectionIds++;
    this.logger = new LogWrapper(context.getLogger(), "PersistentConnection", "pc_" + connId);
    this.lastSessionId = null;
    doIdleCheck();
  }

  // Connection.Delegate methods
  @Override
  public void onReady(long timestamp, String sessionId) {
    if (logger.logsDebug()) logger.debug("onReady");
    lastConnectionEstablishedTime = System.currentTimeMillis();
    handleTimestamp(timestamp);

    if (this.firstConnection) {
      sendConnectStats();
    }

    restoreAuth();
    this.firstConnection = false;
    this.lastSessionId = sessionId;
    delegate.onConnect();
  }

  @Override
  public void onCacheHost(String host) {
    this.cachedHost = host;
  }

  @Override
  public void listen(
      List<String> path,
      Map<String, Object> queryParams,
      ListenHashProvider currentHashFn,
      Long tag,
      RequestResultCallback listener) {
    QuerySpec query = new QuerySpec(path, queryParams);
    if (logger.logsDebug()) logger.debug("Listening on " + query);
    // TODO: Fix this somehow?
    // hardAssert(query.isDefault() || !query.loadsAllData(), "listen() called for non-default but
    // complete query");
    hardAssert(!listens.containsKey(query), "listen() called twice for same QuerySpec.");
    if (logger.logsDebug()) logger.debug("Adding listen query: " + query);
    OutstandingListen outstandingListen =
        new OutstandingListen(listener, query, tag, currentHashFn);
    listens.put(query, outstandingListen);
    if (connected()) {
      sendListen(outstandingListen);
    }
    doIdleCheck();
  }

  @Override
  public Task<Object> get(List<String> path, Map<String, Object> queryParams) {
    QuerySpec query = new QuerySpec(path, queryParams);
    TaskCompletionSource<Object> source = new TaskCompletionSource<>();

    long readId = this.readCounter++;

    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(query.path));
    request.put(REQUEST_QUERIES, query.queryParams);

    OutstandingGet outstandingGet =
        new OutstandingGet(
            REQUEST_ACTION_GET,
            request,
            new ConnectionRequestCallback() {
              @Override
              public void onResponse(Map<String, Object> response) {
                String status = (String) response.get(REQUEST_STATUS);
                if (status.equals("ok")) {
                  Object body = response.get(SERVER_DATA_UPDATE_BODY);
                  delegate.onDataUpdate(query.path, body, /*isMerge=*/ false, /*tagNumber=*/ null);
                  source.setResult(body);
                } else {
                  source.setException(
                      new Exception((String) response.get(SERVER_DATA_UPDATE_BODY)));
                }
              }
            });
    outstandingGets.put(readId, outstandingGet);

    if (!connected()) {
      executorService.schedule(
          new Runnable() {
            @Override
            public void run() {
              if (!outstandingGet.markSent()) {
                return;
              }
              if (logger.logsDebug()) {
                logger.debug("get " + readId + " timed out waiting for connection");
              }
              outstandingGets.remove(readId);
              source.setException(new Exception("Client is offline"));
            }
          },
          GET_CONNECT_TIMEOUT,
          TimeUnit.MILLISECONDS);
    }

    if (canSendReads()) {
      sendGet(readId);
    }
    doIdleCheck();
    return source.getTask();
  }

  @Override
  public void initialize() {
    this.tryScheduleReconnect();
  }

  @Override
  public void shutdown() {
    this.interrupt("shutdown");
  }

  @Override
  public void put(List<String> path, Object data, RequestResultCallback onComplete) {
    putInternal(REQUEST_ACTION_PUT, path, data, /*hash=*/ null, onComplete);
  }

  @Override
  public void compareAndPut(
      List<String> path, Object data, String hash, RequestResultCallback onComplete) {
    putInternal(REQUEST_ACTION_PUT, path, data, hash, onComplete);
  }

  @Override
  public void merge(List<String> path, Map<String, Object> data, RequestResultCallback onComplete) {
    putInternal(REQUEST_ACTION_MERGE, path, data, /*hash=*/ null, onComplete);
  }

  @Override
  public void purgeOutstandingWrites() {
    for (OutstandingPut put : this.outstandingPuts.values()) {
      if (put.onComplete != null) {
        put.onComplete.onRequestResult("write_canceled", null);
      }
    }
    for (OutstandingDisconnect onDisconnect : this.onDisconnectRequestQueue) {
      if (onDisconnect.onComplete != null) {
        onDisconnect.onComplete.onRequestResult("write_canceled", null);
      }
    }
    this.outstandingPuts.clear();
    this.onDisconnectRequestQueue.clear();
    // Only if we are not connected can we reliably determine that we don't have onDisconnects
    // (outstanding) anymore. Otherwise we leave the flag untouched.
    if (!connected()) {
      this.hasOnDisconnects = false;
    }
    doIdleCheck();
  }

  @Override
  public void onDataMessage(Map<String, Object> message) {
    if (message.containsKey(REQUEST_NUMBER)) {
      // this is a response to a request we sent
      // TODO: this is a hack. Make the json parser give us a Long
      long rn = (Integer) message.get(REQUEST_NUMBER);
      ConnectionRequestCallback responseListener = requestCBHash.remove(rn);
      if (responseListener != null) {
        // jackson gives up Map<String, Object> for json objects
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) message.get(RESPONSE_FOR_REQUEST);
        responseListener.onResponse(response);
      }
    } else if (message.containsKey(REQUEST_ERROR)) {
      // TODO: log the error? probably shouldn't throw here...
    } else if (message.containsKey(SERVER_ASYNC_ACTION)) {
      String action = (String) message.get(SERVER_ASYNC_ACTION);
      // jackson gives up Map<String, Object> for json objects
      @SuppressWarnings("unchecked")
      Map<String, Object> body = (Map<String, Object>) message.get(SERVER_ASYNC_PAYLOAD);
      onDataPush(action, body);
    } else {
      if (logger.logsDebug()) logger.debug("Ignoring unknown message: " + message);
    }
  }

  @Override
  public void onDisconnect(Connection.DisconnectReason reason) {
    if (logger.logsDebug()) logger.debug("Got on disconnect due to " + reason.name());
    this.connectionState = ConnectionState.Disconnected;
    this.realtime = null;
    this.hasOnDisconnects = false;
    requestCBHash.clear();
    cancelSentTransactions();
    if (shouldReconnect()) {
      long timeSinceLastConnectSucceeded =
          System.currentTimeMillis() - lastConnectionEstablishedTime;
      boolean lastConnectionWasSuccessful;
      if (lastConnectionEstablishedTime > 0) {
        lastConnectionWasSuccessful =
            timeSinceLastConnectSucceeded > SUCCESSFUL_CONNECTION_ESTABLISHED_DELAY;
      } else {
        lastConnectionWasSuccessful = false;
      }
      if (reason == Connection.DisconnectReason.SERVER_RESET || lastConnectionWasSuccessful) {
        retryHelper.signalSuccess();
      }
      tryScheduleReconnect();
    }
    lastConnectionEstablishedTime = 0;
    delegate.onDisconnect();
  }

  @Override
  public void onKill(String reason) {
    if (logger.logsDebug())
      logger.debug(
          "Firebase Database connection was forcefully killed by the server. Will not attempt reconnect. Reason: "
              + reason);
    interrupt(SERVER_KILL_INTERRUPT_REASON);
  }

  @Override
  public void unlisten(List<String> path, Map<String, Object> queryParams) {
    QuerySpec query = new QuerySpec(path, queryParams);
    if (logger.logsDebug()) logger.debug("unlistening on " + query);

    // TODO: fix this by understanding query params?
    // Utilities.hardAssert(query.isDefault() || !query.loadsAllData(), "unlisten() called for
    // non-default but complete query");
    OutstandingListen listen = removeListen(query);
    if (listen != null && connected()) {
      sendUnlisten(listen);
    }
    doIdleCheck();
  }

  private boolean connected() {
    return connectionState == ConnectionState.Authenticating
        || connectionState == ConnectionState.Connected;
  }

  @Override
  public void onDisconnectPut(List<String> path, Object data, RequestResultCallback onComplete) {
    this.hasOnDisconnects = true;
    if (canSendWrites()) {
      sendOnDisconnect(REQUEST_ACTION_ONDISCONNECT_PUT, path, data, onComplete);
    } else {
      onDisconnectRequestQueue.add(
          new OutstandingDisconnect(REQUEST_ACTION_ONDISCONNECT_PUT, path, data, onComplete));
    }
    doIdleCheck();
  }

  private boolean canSendWrites() {
    return connectionState == ConnectionState.Connected;
  }

  private boolean canSendReads() {
    return connectionState == ConnectionState.Connected;
  }

  @Override
  public void onDisconnectMerge(
      List<String> path, Map<String, Object> updates, final RequestResultCallback onComplete) {
    this.hasOnDisconnects = true;
    if (canSendWrites()) {
      sendOnDisconnect(REQUEST_ACTION_ONDISCONNECT_MERGE, path, updates, onComplete);
    } else {
      onDisconnectRequestQueue.add(
          new OutstandingDisconnect(REQUEST_ACTION_ONDISCONNECT_MERGE, path, updates, onComplete));
    }
    doIdleCheck();
  }

  @Override
  public void onDisconnectCancel(List<String> path, RequestResultCallback onComplete) {
    // We do not mark hasOnDisconnects true here, because we only are removing disconnects.
    // However, we can also not reliably determine whether we had onDisconnects, so we can't
    // and do not reset the flag.
    if (canSendWrites()) {
      sendOnDisconnect(REQUEST_ACTION_ONDISCONNECT_CANCEL, path, null, onComplete);
    } else {
      onDisconnectRequestQueue.add(
          new OutstandingDisconnect(REQUEST_ACTION_ONDISCONNECT_CANCEL, path, null, onComplete));
    }
    doIdleCheck();
  }

  @Override
  public void interrupt(String reason) {
    if (logger.logsDebug()) logger.debug("Connection interrupted for: " + reason);
    interruptReasons.add(reason);

    if (realtime != null) {
      // Will call onDisconnect and set the connection state to Disconnected
      realtime.close();
      realtime = null;
    } else {
      retryHelper.cancel();
      this.connectionState = ConnectionState.Disconnected;
    }
    // Reset timeouts
    retryHelper.signalSuccess();
  }

  @Override
  public void resume(String reason) {
    if (logger.logsDebug()) {
      logger.debug("Connection no longer interrupted for: " + reason);
    }

    interruptReasons.remove(reason);

    if (shouldReconnect() && connectionState == ConnectionState.Disconnected) {
      tryScheduleReconnect();
    }
  }

  @Override
  public boolean isInterrupted(String reason) {
    return interruptReasons.contains(reason);
  }

  boolean shouldReconnect() {
    return interruptReasons.size() == 0;
  }

  @Override
  public void refreshAuthToken() {
    // Old versions of the database client library didn't have synchronous access to the
    // new token and call this instead of the overload that includes the new token.

    // After a refresh token any subsequent operations are expected to have the authentication
    // status at the point of this call. To avoid race conditions with delays after getToken,
    // we close the connection to make sure any writes/listens are queued until the connection
    // is reauthed with the current token after reconnecting. Note that this will trigger
    // onDisconnects which isn't ideal.
    logger.debug("Auth token refresh requested");

    // By using interrupt instead of closing the connection we make sure there are no race
    // conditions with other fetch token attempts (interrupt/resume is expected to handle those
    // correctly)
    interrupt(TOKEN_REFRESH_INTERRUPT_REASON);
    resume(TOKEN_REFRESH_INTERRUPT_REASON);
  }

  @Override
  public void refreshAuthToken(String token) {
    logger.debug("Auth token refreshed.");
    this.authToken = token;
    if (connected()) {
      if (token != null) {
        upgradeAuth();
      } else {
        sendUnauth();
      }
    }
  }

  private void tryScheduleReconnect() {
    if (shouldReconnect()) {
      hardAssert(
          this.connectionState == ConnectionState.Disconnected,
          "Not in disconnected state: %s",
          this.connectionState);
      final boolean forceRefresh = this.forceAuthTokenRefresh;
      logger.debug("Scheduling connection attempt");
      this.forceAuthTokenRefresh = false;
      retryHelper.retry(
          new Runnable() {
            @Override
            public void run() {
              logger.debug("Trying to fetch auth token");
              hardAssert(
                  connectionState == ConnectionState.Disconnected,
                  "Not in disconnected state: %s",
                  connectionState);
              connectionState = ConnectionState.GettingToken;
              currentGetTokenAttempt++;
              final long thisGetTokenAttempt = currentGetTokenAttempt;
              authTokenProvider.getToken(
                  forceRefresh,
                  new ConnectionAuthTokenProvider.GetTokenCallback() {
                    @Override
                    public void onSuccess(String token) {
                      if (thisGetTokenAttempt == currentGetTokenAttempt) {
                        // Someone could have interrupted us while fetching the token,
                        // marking the connection as Disconnected
                        if (connectionState == ConnectionState.GettingToken) {
                          logger.debug("Successfully fetched token, opening connection");
                          openNetworkConnection(token);
                        } else {
                          hardAssert(
                              connectionState == ConnectionState.Disconnected,
                              "Expected connection state disconnected, but was %s",
                              connectionState);
                          logger.debug(
                              "Not opening connection after token refresh, "
                                  + "because connection was set to disconnected");
                        }
                      } else {
                        logger.debug(
                            "Ignoring getToken result, because this was not the "
                                + "latest attempt.");
                      }
                    }

                    @Override
                    public void onError(String error) {
                      if (thisGetTokenAttempt == currentGetTokenAttempt) {
                        connectionState = ConnectionState.Disconnected;
                        logger.debug("Error fetching token: " + error);
                        tryScheduleReconnect();
                      } else {
                        logger.debug(
                            "Ignoring getToken error, because this was not the "
                                + "latest attempt.");
                      }
                    }
                  });
            }
          });
    }
  }

  public void openNetworkConnection(String token) {
    hardAssert(
        this.connectionState == ConnectionState.GettingToken,
        "Trying to open network connection while in the wrong state: %s",
        this.connectionState);
    // User might have logged out. Positive auth status is handled after authenticating with
    // the server
    if (token == null) {
      this.delegate.onAuthStatus(false);
    }
    this.authToken = token;
    this.connectionState = ConnectionState.Connecting;
    realtime =
        new Connection(this.context, this.hostInfo, this.cachedHost, this, this.lastSessionId);
    realtime.open();
  }

  private void sendOnDisconnect(
      String action, List<String> path, Object data, final RequestResultCallback onComplete) {
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(path));
    request.put(REQUEST_DATA_PAYLOAD, data);
    //
    // if (logger.logsDebug()) logger.debug("onDisconnect " + action + " " + request);
    sendAction(
        action,
        request,
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            String status = (String) response.get(REQUEST_STATUS);
            String errorMessage = null;
            String errorCode = null;
            if (!status.equals("ok")) {
              errorCode = status;
              errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
            }
            if (onComplete != null) {
              onComplete.onRequestResult(errorCode, errorMessage);
            }
          }
        });
  }

  private void cancelSentTransactions() {
    List<OutstandingPut> cancelledTransactionWrites = new ArrayList<>();

    Iterator<Map.Entry<Long, OutstandingPut>> iter = outstandingPuts.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<Long, OutstandingPut> entry = iter.next();
      OutstandingPut put = entry.getValue();
      if (put.getRequest().containsKey(REQUEST_DATA_HASH) && put.wasSent()) {
        cancelledTransactionWrites.add(put);
        iter.remove();
      }
    }

    for (OutstandingPut put : cancelledTransactionWrites) {
      // onRequestResult() may invoke rerunTransactions() and enqueue new writes. We defer
      // calling it until we've finished enumerating all existing writes.
      put.getOnComplete().onRequestResult("disconnected", null);
    }
  }

  private void sendUnlisten(OutstandingListen listen) {
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(listen.query.path));

    Long tag = listen.getTag();
    if (tag != null) {
      request.put(REQUEST_QUERIES, listen.getQuery().queryParams);
      request.put(REQUEST_TAG, tag);
    }

    sendAction(REQUEST_ACTION_QUERY_UNLISTEN, request, null);
  }

  private OutstandingListen removeListen(QuerySpec query) {
    if (logger.logsDebug()) logger.debug("removing query " + query);
    if (!listens.containsKey(query)) {
      if (logger.logsDebug())
        logger.debug(
            "Trying to remove listener for QuerySpec " + query + " but no listener exists.");
      return null;
    } else {
      OutstandingListen oldListen = listens.get(query);
      listens.remove(query);
      doIdleCheck();
      return oldListen;
    }
  }

  private Collection<OutstandingListen> removeListens(List<String> path) {
    if (logger.logsDebug()) logger.debug("removing all listens at path " + path);
    List<OutstandingListen> removedListens = new ArrayList<OutstandingListen>();
    for (Map.Entry<QuerySpec, OutstandingListen> entry : listens.entrySet()) {
      QuerySpec query = entry.getKey();
      OutstandingListen listen = entry.getValue();
      if (query.path.equals(path)) {
        removedListens.add(listen);
      }
    }

    for (OutstandingListen toRemove : removedListens) {
      listens.remove(toRemove.getQuery());
    }

    doIdleCheck();

    return removedListens;
  }

  private void onDataPush(String action, Map<String, Object> body) {
    if (logger.logsDebug()) logger.debug("handleServerMessage: " + action + " " + body);
    if (action.equals(SERVER_ASYNC_DATA_UPDATE) || action.equals(SERVER_ASYNC_DATA_MERGE)) {
      boolean isMerge = action.equals(SERVER_ASYNC_DATA_MERGE);

      String pathString = (String) body.get(SERVER_DATA_UPDATE_PATH);
      Object payloadData = body.get(SERVER_DATA_UPDATE_BODY);
      Long tagNumber = ConnectionUtils.longFromObject(body.get(SERVER_DATA_TAG));
      // ignore empty merges
      if (isMerge && (payloadData instanceof Map) && ((Map) payloadData).size() == 0) {
        if (logger.logsDebug()) logger.debug("ignoring empty merge for path " + pathString);
      } else {
        List<String> path = ConnectionUtils.stringToPath(pathString);
        delegate.onDataUpdate(path, payloadData, isMerge, tagNumber);
      }
    } else if (action.equals(SERVER_ASYNC_DATA_RANGE_MERGE)) {
      String pathString = (String) body.get(SERVER_DATA_UPDATE_PATH);
      List<String> path = ConnectionUtils.stringToPath(pathString);
      Object payloadData = body.get(SERVER_DATA_UPDATE_BODY);
      Long tag = ConnectionUtils.longFromObject(body.get(SERVER_DATA_TAG));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> ranges = (List<Map<String, Object>>) payloadData;
      List<RangeMerge> rangeMerges = new ArrayList<RangeMerge>();
      for (Map<String, Object> range : ranges) {
        String startString = (String) range.get(SERVER_DATA_START_PATH);
        String endString = (String) range.get(SERVER_DATA_END_PATH);
        List<String> start = startString != null ? ConnectionUtils.stringToPath(startString) : null;
        List<String> end = endString != null ? ConnectionUtils.stringToPath(endString) : null;
        Object update = range.get(SERVER_DATA_RANGE_MERGE);
        rangeMerges.add(new RangeMerge(start, end, update));
      }
      if (rangeMerges.isEmpty()) {
        if (logger.logsDebug()) logger.debug("Ignoring empty range merge for path " + pathString);
      } else {
        this.delegate.onRangeMergeUpdate(path, rangeMerges, tag);
      }
    } else if (action.equals(SERVER_ASYNC_LISTEN_CANCELLED)) {
      String pathString = (String) body.get(SERVER_DATA_UPDATE_PATH);
      List<String> path = ConnectionUtils.stringToPath(pathString);
      onListenRevoked(path);
    } else if (action.equals(SERVER_ASYNC_AUTH_REVOKED)) {
      String status = (String) body.get(REQUEST_STATUS);
      String reason = (String) body.get(SERVER_DATA_UPDATE_BODY);
      onAuthRevoked(status, reason);
    } else if (action.equals(SERVER_ASYNC_SECURITY_DEBUG)) {
      onSecurityDebugPacket(body);
    } else {
      if (logger.logsDebug()) logger.debug("Unrecognized action from server: " + action);
    }
  }

  private void onListenRevoked(List<String> path) {
    // Remove the listen and manufacture a "permission denied" error for the failed listen

    Collection<OutstandingListen> listens = removeListens(path);
    // The listen may have already been removed locally. If so, skip it
    if (listens != null) {
      for (OutstandingListen listen : listens) {
        listen.resultCallback.onRequestResult("permission_denied", null);
      }
    }
  }

  private void onAuthRevoked(String errorCode, String errorMessage) {
    // This might be for an earlier token than we just recently sent. But since we need to close
    // the connection anyways, we can set it to null here and we will refresh the token later
    // on reconnect.
    logger.debug("Auth token revoked: " + errorCode + " (" + errorMessage + ")");
    this.authToken = null;
    this.forceAuthTokenRefresh = true;
    this.delegate.onAuthStatus(false);
    // Close connection and reconnect
    this.realtime.close();
  }

  private void onSecurityDebugPacket(Map<String, Object> message) {
    // TODO: implement on iOS too
    logger.info((String) message.get("msg"));
  }

  private void upgradeAuth() {
    sendAuthHelper(/*restoreStateAfterComplete=*/ false);
  }

  private void sendAuthAndRestoreState() {
    sendAuthHelper(/*restoreStateAfterComplete=*/ true);
  }

  private void sendAuthHelper(final boolean restoreStateAfterComplete) {
    hardAssert(connected(), "Must be connected to send auth, but was: %s", this.connectionState);
    hardAssert(this.authToken != null, "Auth token must be set to authenticate!");

    ConnectionRequestCallback onComplete =
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            connectionState = ConnectionState.Connected;

            String status = (String) response.get(REQUEST_STATUS);
            if (status.equals("ok")) {
              invalidAuthTokenCount = 0;
              delegate.onAuthStatus(true);
              if (restoreStateAfterComplete) {
                restoreState();
              }
            } else {
              authToken = null;
              forceAuthTokenRefresh = true;
              delegate.onAuthStatus(false);
              String reason = (String) response.get(SERVER_RESPONSE_DATA);
              logger.debug("Authentication failed: " + status + " (" + reason + ")");
              realtime.close();

              if (status.equals("invalid_token")) {
                // We'll wait a couple times before logging the warning / increasing the
                // retry period since oauth tokens will report as "invalid" if they're
                // just expired. Plus there may be transient issues that resolve themselves.
                invalidAuthTokenCount++;
                if (invalidAuthTokenCount >= INVALID_AUTH_TOKEN_THRESHOLD) {
                  // Set a long reconnect delay because recovery is unlikely.
                  retryHelper.setMaxDelay();
                  logger.warn(
                      "Provided authentication credentials are invalid. This "
                          + "usually indicates your FirebaseApp instance was not initialized "
                          + "correctly. Make sure your google-services.json file has the "
                          + "correct firebase_url and api_key. You can re-download "
                          + "google-services.json from "
                          + "https://console.firebase.google.com/.");
                }
              }
            }
          }
        };

    Map<String, Object> request = new HashMap<String, Object>();
    GAuthToken gAuthToken = GAuthToken.tryParseFromString(this.authToken);
    if (gAuthToken != null) {
      request.put(REQUEST_CREDENTIAL, gAuthToken.getToken());
      if (gAuthToken.getAuth() != null) {
        request.put(REQUEST_AUTHVAR, gAuthToken.getAuth());
      }
      sendSensitive(REQUEST_ACTION_GAUTH, /*isSensitive=*/ true, request, onComplete);
    } else {
      request.put(REQUEST_CREDENTIAL, authToken);
      sendSensitive(REQUEST_ACTION_AUTH, /*isSensitive=*/ true, request, onComplete);
    }
  }

  private void sendUnauth() {
    hardAssert(connected(), "Must be connected to send unauth.");
    hardAssert(authToken == null, "Auth token must not be set.");
    sendAction(REQUEST_ACTION_UNAUTH, Collections.<String, Object>emptyMap(), null);
  }

  private void restoreAuth() {
    if (logger.logsDebug()) logger.debug("calling restore state");

    hardAssert(
        this.connectionState == ConnectionState.Connecting,
        "Wanted to restore auth, but was in wrong state: %s",
        this.connectionState);

    if (authToken == null) {
      if (logger.logsDebug()) logger.debug("Not restoring auth because token is null.");
      this.connectionState = ConnectionState.Connected;
      restoreState();
    } else {
      if (logger.logsDebug()) logger.debug("Restoring auth.");
      this.connectionState = ConnectionState.Authenticating;
      sendAuthAndRestoreState();
    }
  }

  private void restoreState() {
    hardAssert(
        this.connectionState == ConnectionState.Connected,
        "Should be connected if we're restoring state, but we are: %s",
        this.connectionState);

    // Restore listens
    if (logger.logsDebug()) logger.debug("Restoring outstanding listens");
    for (OutstandingListen listen : listens.values()) {
      if (logger.logsDebug()) logger.debug("Restoring listen " + listen.getQuery());
      sendListen(listen);
    }

    if (logger.logsDebug()) logger.debug("Restoring writes.");
    // Restore puts
    ArrayList<Long> outstanding = new ArrayList<Long>(outstandingPuts.keySet());
    // Make sure puts are restored in order
    Collections.sort(outstanding);
    for (Long put : outstanding) {
      sendPut(put);
    }

    // Restore disconnect operations
    for (OutstandingDisconnect disconnect : onDisconnectRequestQueue) {
      sendOnDisconnect(
          disconnect.getAction(),
          disconnect.getPath(),
          disconnect.getData(),
          disconnect.getOnComplete());
    }
    onDisconnectRequestQueue.clear();

    if (logger.logsDebug()) logger.debug("Restoring reads.");
    ArrayList<Long> outstandingGetKeys = new ArrayList<Long>(outstandingGets.keySet());
    Collections.sort(outstandingGetKeys);
    for (Long getId : outstandingGetKeys) {
      sendGet(getId);
    }
  }

  private void handleTimestamp(long timestamp) {
    if (logger.logsDebug()) logger.debug("handling timestamp");
    long timestampDelta = timestamp - System.currentTimeMillis();
    Map<String, Object> updates = new HashMap<String, Object>();
    updates.put(Constants.DOT_INFO_SERVERTIME_OFFSET, timestampDelta);
    delegate.onServerInfoUpdate(updates);
  }

  private Map<String, Object> getPutObject(List<String> path, Object data, String hash) {
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(path));
    request.put(REQUEST_DATA_PAYLOAD, data);
    if (hash != null) {
      request.put(REQUEST_DATA_HASH, hash);
    }
    return request;
  }

  private void putInternal(
      String action,
      List<String> path,
      Object data,
      String hash,
      RequestResultCallback onComplete) {
    Map<String, Object> request = getPutObject(path, data, hash);

    // local to PersistentConnection
    long writeId = this.writeCounter++;

    outstandingPuts.put(writeId, new OutstandingPut(action, request, onComplete));
    if (canSendWrites()) {
      sendPut(writeId);
    }
    this.lastWriteTimestamp = System.currentTimeMillis();
    doIdleCheck();
  }

  private void sendPut(final long putId) {
    hardAssert(
        canSendWrites(),
        "sendPut called when we can't send writes (we're disconnected or writes are paused).");
    final OutstandingPut put = outstandingPuts.get(putId);
    final RequestResultCallback onComplete = put.getOnComplete();
    final String action = put.getAction();

    put.markSent();
    sendAction(
        action,
        put.getRequest(),
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            if (logger.logsDebug()) logger.debug(action + " response: " + response);

            OutstandingPut currentPut = outstandingPuts.get(putId);
            if (currentPut == put) {
              outstandingPuts.remove(putId);

              if (onComplete != null) {
                String status = (String) response.get(REQUEST_STATUS);
                if (status.equals("ok")) {
                  onComplete.onRequestResult(null, null);
                } else {
                  String errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
                  onComplete.onRequestResult(status, errorMessage);
                }
              }
            } else {
              if (logger.logsDebug())
                logger.debug(
                    "Ignoring on complete for put " + putId + " because it was removed already.");
            }
            doIdleCheck();
          }
        });
  }

  private void sendGet(final Long readId) {
    hardAssert(canSendReads(), "sendGet called when we can't send gets");
    OutstandingGet get = outstandingGets.get(readId);
    if (!get.markSent()) {
      if (logger.logsDebug()) {
        logger.debug("get" + readId + " cancelled, ignoring.");
        return;
      }
    }
    sendAction(
        REQUEST_ACTION_GET,
        get.getRequest(),
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            OutstandingGet currentGet = outstandingGets.get(readId);
            if (currentGet == get) {
              outstandingGets.remove(readId);
              get.getOnComplete().onResponse(response);
            } else if (logger.logsDebug()) {
              logger.debug(
                  "Ignoring on complete for get " + readId + " because it was removed already.");
            }
          }
        });
  }

  private void sendListen(final OutstandingListen listen) {
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(listen.getQuery().path));
    Long tag = listen.getTag();
    // Only bother to send query if it's non-default
    if (tag != null) {
      request.put(REQUEST_QUERIES, listen.query.queryParams);
      request.put(REQUEST_TAG, tag);
    }

    ListenHashProvider hashFunction = listen.getHashFunction();
    request.put(REQUEST_DATA_HASH, hashFunction.getSimpleHash());

    if (hashFunction.shouldIncludeCompoundHash()) {
      CompoundHash compoundHash = hashFunction.getCompoundHash();

      List<String> posts = new ArrayList<String>();
      for (List<String> path : compoundHash.getPosts()) {
        posts.add(ConnectionUtils.pathToString(path));
      }
      Map<String, Object> hash = new HashMap<String, Object>();
      hash.put(REQUEST_COMPOUND_HASH_HASHES, compoundHash.getHashes());
      hash.put(REQUEST_COMPOUND_HASH_PATHS, posts);
      request.put(REQUEST_COMPOUND_HASH, hash);
    }

    sendAction(
        REQUEST_ACTION_QUERY,
        request,
        new ConnectionRequestCallback() {

          @Override
          public void onResponse(Map<String, Object> response) {
            String status = (String) response.get(REQUEST_STATUS);
            // log warnings in any case, even if listener was already removed
            if (status.equals("ok")) {
              @SuppressWarnings("unchecked")
              Map<String, Object> serverBody =
                  (Map<String, Object>) response.get(SERVER_DATA_UPDATE_BODY);
              if (serverBody.containsKey(SERVER_DATA_WARNINGS)) {
                @SuppressWarnings("unchecked")
                List<String> warnings = (List<String>) serverBody.get(SERVER_DATA_WARNINGS);
                warnOnListenerWarnings(warnings, listen.query);
              }
            }

            OutstandingListen currentListen = listens.get(listen.getQuery());
            // only trigger actions if the listen hasn't been removed (and maybe readded)
            if (currentListen == listen) {
              if (!status.equals("ok")) {
                removeListen(listen.getQuery());
                String errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
                listen.resultCallback.onRequestResult(status, errorMessage);
              } else {
                listen.resultCallback.onRequestResult(null, null);
              }
            }
          }
        });
  }

  private void sendStats(final Map<String, Integer> stats) {
    if (!stats.isEmpty()) {
      Map<String, Object> request = new HashMap<String, Object>();
      request.put(REQUEST_COUNTERS, stats);
      sendAction(
          REQUEST_ACTION_STATS,
          request,
          new ConnectionRequestCallback() {
            @Override
            public void onResponse(Map<String, Object> response) {
              String status = (String) response.get(REQUEST_STATUS);
              if (!status.equals("ok")) {
                String errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
                if (logger.logsDebug()) {
                  logger.debug(
                      "Failed to send stats: " + status + " (message: " + errorMessage + ")");
                }
              }
            }
          });
    } else {
      if (logger.logsDebug()) logger.debug("Not sending stats because stats are empty");
    }
  }

  @SuppressWarnings("unchecked")
  private void warnOnListenerWarnings(List<String> warnings, QuerySpec query) {
    if (warnings.contains("no_index")) {
      String indexSpec = "\".indexOn\": \"" + query.queryParams.get("i") + '\"';
      logger.warn(
          "Using an unspecified index. Your data will be downloaded and filtered "
              + "on the client. Consider adding '"
              + indexSpec
              + "' at "
              + ConnectionUtils.pathToString(query.path)
              + " to your security and Firebase Database rules for better performance");
    }
  }

  private void sendConnectStats() {
    Map<String, Integer> stats = new HashMap<String, Integer>();
    if (this.context.isPersistenceEnabled()) {
      stats.put("persistence.android.enabled", 1);
    }
    stats.put("sdk.android." + context.getClientSdkVersion().replace('.', '-'), 1);
    // TODO: Also send stats for connection version
    if (logger.logsDebug()) logger.debug("Sending first connection stats");
    sendStats(stats);
  }

  private void sendAction(
      String action, Map<String, Object> message, ConnectionRequestCallback onResponse) {
    sendSensitive(action, /*isSensitive=*/ false, message, onResponse);
  }

  private void sendSensitive(
      String action,
      boolean isSensitive,
      Map<String, Object> message,
      ConnectionRequestCallback onResponse) {
    long rn = nextRequestNumber();
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_NUMBER, rn);
    request.put(REQUEST_ACTION, action);
    request.put(REQUEST_PAYLOAD, message);
    realtime.sendRequest(request, isSensitive);
    requestCBHash.put(rn, onResponse);
  }

  private long nextRequestNumber() {
    return requestCounter++;
  }

  private void doIdleCheck() {
    if (isIdle()) {
      if (this.inactivityTimer != null) {
        this.inactivityTimer.cancel(false);
      }

      this.inactivityTimer =
          this.executorService.schedule(
              new Runnable() {
                @Override
                public void run() {
                  inactivityTimer = null;
                  if (idleHasTimedOut()) {
                    interrupt(IDLE_INTERRUPT_REASON);
                  } else {
                    doIdleCheck();
                  }
                }
              },
              IDLE_TIMEOUT,
              TimeUnit.MILLISECONDS);
    } else if (isInterrupted(IDLE_INTERRUPT_REASON)) {
      hardAssert(!isIdle());
      this.resume(IDLE_INTERRUPT_REASON);
    }
  }

  /**
   * @return Returns true if the connection is currently not being used (for listen, outstanding
   *     operations).
   */
  private boolean isIdle() {
    return this.listens.isEmpty()
        && this.requestCBHash.isEmpty()
        && !this.hasOnDisconnects
        && this.outstandingPuts.isEmpty();
  }

  private boolean idleHasTimedOut() {
    long now = System.currentTimeMillis();
    return isIdle() && now > (this.lastWriteTimestamp + IDLE_TIMEOUT);
  }

  // For testing
  public void injectConnectionFailure() {
    if (this.realtime != null) {
      this.realtime.injectConnectionFailure();
    }
  }
}
