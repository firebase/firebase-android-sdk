# Realtime Query Subscription TODO List

### TODO 1: Lack of Connection Health Monitoring / Reconnection

* **File:** RealtimeQueryManager.kt
* **Severity:** `CRITICAL`

#### Description

Once `RealtimeQueryManager` successfully transitions to `State.Connected(stream)`, it remains in
this state permanently. If the underlying bidirectional gRPC connection is lost or the stream is
closed (due to network issues, server-side termination, or client-side close), the manager does not
detect this. Any future calls to `subscribe()` will read `State.Connected` and try to use the dead
stream, causing new subscriptions to silently hang or fail indefinitely without any reconnection
attempts.

#### Recommendation

Add connection health monitoring or detect when the stream has completed/failed, and transition
the manager's state back to `State.Disconnected` (or clean up resources) to allow subsequent
subscription calls to trigger a new connection.

---

### TODO 2: Permanent Lock-out / Stuck in `Connecting` State on Connection Failure

* **File:** RealtimeQueryManager.kt
* **Severity:** `CRITICAL`

#### Description

In `ensureConnected`, `currentState.job.await()` is called to wait for the lazy `Deferred`
connection job. If the connection attempt fails (e.g., due to a temporary network issue) and
throws an exception, the exception propagates out of the method. Because the exception is thrown
before the state is updated, the manager's state remains permanently stuck in
`State.Connecting(job)`. Any future connection attempts will call `await()` on the same failed
`Deferred` job, which immediately and permanently re-throws the same cached exception, making the
manager completely unusable until the app or SDK instance is restarted.

### TODO 3: Memory and Resource Leak of Subscription Flows in `flowByQueryId`

* **File:** RealtimeQueryManager.kt
* **Severity:** `HIGH`

#### Description

Active subscription flows are stored in the `flowByQueryId` map to deduplicate identical queries.
However, there is no mechanism to remove flows from this map when a subscription is cancelled,
completed, or when there are no active collectors left. This leads to an unbounded memory/resource
leak as the client subscribes to different queries over time. Furthermore, because the subscription
is never cleaned up, the backend stream may continue sending updates for cancelled subscriptions,
wasting bandwidth and server resources.

#### Recommendation

Implement a reference-counting mechanism or a cleanup callback upon flow completion to remove the
query from `flowByQueryId` once the active collector count drops to zero.

### TODO 3: Memory and Resource Leak of Subscription Flows in `flowByQueryId`

* **File:** DataConnectBidiConnectStream.kt
* **Severity:** `HIGH`

#### Description

Late subscribers to a stream that has already completed (server-side) will hang indefinitely.
This occurs because `incomingResponses` is a `SharedFlow` with `replay = 0`. If the stream
completes, the `IncomingResponse.Completed` signal is emitted and lost for future subscribers.
These subscribers will then wait in `transformWhile` for new emissions that will never come.

The current implementation of `onCompletion` does not prevent the hang for late subscribers.
Because `streams.incomingResponses` is a `SharedFlow` with `replay = 0`, if the stream has already
completed, the `IncomingResponse.Completed` signal is lost. A new subscriber will begin collecting
from `incomingResponses` and wait indefinitely for emissions that will never arrive. The
`onCompletion` block is only executed *after* the flow collection completes, so it cannot resolve a
hang that occurs *during* the collection process.

Discovered by gemini code assist:
https://github.com/firebase/firebase-android-sdk/pull/8158#discussion_r3240286966

#### Recommendation

You should check `streams.completedResponse` before starting the flow collection to ensure the
stream is still active.

To address this, you should check the state of `streams.completedResponse` *before* or *at the
start* of the flow collection (e.g., inside the `flow { ... }` builder) to verify if the stream is
already finished, and emit the completion signal or throw the cached exception immediately if it is.
