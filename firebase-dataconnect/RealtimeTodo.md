# Realtime Query Subscription TODO List

### TODO 1: Memory and Resource Leak of Subscription Flows in `flowByQueryId`

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

---

### TODO 2: Simplistic Retry Logic / Lack of Exponential Backoff and Network State Integration

* **File:** DataConnectBidiConnectStream.kt
* **Severity:** `HIGH`

#### Description

The retry logic in `connectionFlow` is extremely simplistic: it retries up to 2 times with a flat
1-second delay between attempts. If a connection is lost due to a sustained network outage (longer
than 2 seconds), the stream will fail permanently and will not attempt to recover even after the
network comes back.

#### Recommendation

Replace the simplistic retry logic with:
1. **Exponential Backoff**: Increase the delay between retries exponentially (with jitter) to avoid
   overwhelming the server and the client.
2. **Network State Integration**: Integrate with the OS network state monitoring (e.g., Android's
   `ConnectivityManager`) to proactively trigger a reconnection attempt as soon as the device
   regains internet connectivity, rather than waiting for a backoff timer.

