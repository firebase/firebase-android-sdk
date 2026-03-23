# QueryManager v2 Implementation

This package contains the next-generation query engine for Firebase Data Connect. It is designed to merge input from local caches, one-off query executions, and query subscriptions while maximizing deduplication and providing entity-level reactivity.

## Implementation Plan

### 1. Objective
Design and implement a new `QueryManager` that merges input from the local cache, one-off queries, and subscriptions. The manager must maximize query deduplication and implement reactivity, notifying subscriptions when their underlying entities are updated by other queries.

### 2. Key Data Structures
- **`RemoteQueryKey`**: Identifies a unique request to the backend.
  - `operationName: String`
  - `variablesHash: ImmutableByteArray` (SHA-512 of variables Struct)
  - `authUid: String?`
- **`LocalQueryKey`**: Identifies a unique local query configuration.
  - `remoteKey: RemoteQueryKey`
  - `fetchPolicy: FetchPolicy`
  - `deserializer: Any`
  - `deserializerModule: Any?`

### 3. Architecture & State Management
The `QueryManager` tracks active requests and subscriptions using Kotlin coroutine-friendly primitives:
- `private val stateMutex = Mutex()`: Protects internal maps.
- `private val activeSubscriptions`: `MutableMap<LocalQueryKey, MutableSharedFlow<QueryResponse>>`.
- `private val inflightRemoteQueries`: `MutableMap<RemoteQueryKey, Deferred<ExecuteQueryResponse>>`.
- `private val entityToSubscriptions`: `MutableMap<String, MutableSet<LocalQueryKey>>`: Tracks which subscriptions depend on which entity IDs.

### 4. Query Execution Logic
- **One-Off Queries**:
  1. Validate `FetchPolicy` (Fallback to `SERVER_ONLY` if cache is disabled).
  2. For `PREFER_CACHE`/`CACHE_ONLY`: Check `activeSubscriptions` for a warm result, then the SQLite cache.
  3. For `SERVER_ONLY` or cache miss: Check `inflightRemoteQueries` to join an existing backend request or start a new one via `async`.
  4. Write results to cache and notify affected subscriptions.
- **Subscriptions**:
  1. Enforce `PREFER_CACHE`.
  2. Deduplicate against `activeSubscriptions`.
  3. Populate a `MutableSharedFlow(replay = 1)` with initial cache/server data and listen for updates.

---

## Progress Report

### ✅ Completed Work
- [x] Defined `RemoteQueryKey` and `LocalQueryKey` with proper hashing logic using `ProtoUtil`.
- [x] Implemented `QueryManager` with `stateMutex` for thread-safe state transitions.
- [x] Implemented remote query deduplication using `Deferred` in `inflightRemoteQueries`.
- [x] Implemented local query deduplication via `activeSubscriptions`.
- [x] Implemented `FetchPolicy` validation and cache-disabled fallback logic.
- [x] Integrated `DataConnectCacheDatabase` for persistence within the manager.

### 🛠 Work Remaining (Phase 6+)
- [ ] **Refactor `DataConnectGrpcRPCs.kt`**: Remove legacy caching/deduplication logic to make it a pure networking abstraction.
- [ ] **Full Subscription Lifecycle**: Implement the background coroutine in `subscribe` to listen to `DataConnectStream` and handle cleanup.
- [ ] **Entity-Level Reactivity**:
  - Implement extraction of `entityIds` from `ExecuteQueryResponse` using `getEntityIdForPathFunction`.
  - Implement the reactive trigger to re-evaluate cached queries when their underlying entities are updated by mutations or other queries.
- [ ] **SDK Integration**: Update `FirebaseDataConnectImpl` and `DataConnectGrpcClient` to use `QueryManager` as the primary query entry point.
- [ ] **Verification**: Add comprehensive unit tests for cross-policy deduplication and cross-query reactivity.
