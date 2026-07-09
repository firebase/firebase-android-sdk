---

id: 0006
title: Hybrid Multiplexing of Realtime and Non-Realtime Query Managers
date: Wed May 20 00:28:23 EDT 2026
status: accepted
deciders: [Jetski, dconeybe]
tags: [network, grpc, coroutines, flow, multiplexing]
-----------------------------------------------------

# Hybrid Multiplexing of Realtime and Non-Realtime Query Managers

## Context

With the graduation of Realtime Queries to a stable feature in SDK version 17.3.0, `QuerySubscription` must support both:
1.  **Realtime Updates**: Server-initiated push updates received over the long-lived bidirectional `Connect` gRPC stream.
2.  **Local Execution Updates**: Client-initiated query executions (via `QueryRef.execute()`) that update the local query cache and should immediately notify active subscribers.

Currently, these responsibilities are split between two independent systems:
*   `QueryManager`: Manages local query executions, local caching, and updates for non-realtime subscribers.
*   `RealtimeQueryManager`: Manages the lifetime of the multiplexed bidirectional `Connect` RPC stream and receives server-pushed results.

These two managers do not communicate. A local query execution via `QueryRef.execute()` runs a standard, unary `ExecuteQuery` RPC which completely bypasses the bidirectional stream. Consequently, the `RealtimeQueryManager` has no knowledge of local query executions, and subscriptions listening solely to the realtime stream would miss updates triggered by local executions.

## Decision

We will temporarily implement a **hybrid multiplexing model** inside `QuerySubscriptionImpl.flow` by combining two distinct coroutine flows:
1.  A subscription flow from `RealtimeQueryManager` to receive server-pushed updates.
2.  A parallel, background subscription flow to `QueryManager` (configured with `executeQuery = false` to prevent duplicate execution) to intercept and forward updates triggered by local, client-side query executions.

This hybrid approach acts as an architectural bridge. It preserves backwards compatibility with the old non-realtime subscription behavior while the backend integration is finalized.

## Rationale

The primary reason for this hybrid architecture is that `RealtimeQueryManager` does not yet capture local query executions because this routing is not yet implemented. Multiplexing the two managers at the subscription level is the simplest and safest way to ensure clients receive both types of updates without breaking existing test suites or client expectations.

### The Long-Term Vision

This hybrid multiplexing is a temporary bridge. The long-term plan is to completely merge `QueryManager` and `RealtimeQueryManager` into a single unified component, eventually renamed to `OperationManager` (since its scope will cover both queries and mutations, for both realtime and oneshot operations).

Once merged, the system will implement **Intelligent Transport Routing** for `executeQuery()` based on the status of the long-lived `Connect` RPC stream:

* **Case 1 (Connect RPC is NOT open)**: Use the standard, unary `ExecuteQuery` RPC.
  * *Rationale*: Setting up a long-lived bidirectional connection just for a oneshot request is inefficient. Using unary calls allows load balancers to route traffic optimally.
* **Case 2 (Connect RPC IS open, and the query HAS an active subscription)**: Send a `resume` request over the existing `Connect` RPC stream (as if a new subscriber joined) and serve the response.
  * *Rationale*: Reuses the existing, active connection, saving connection setup/teardown costs.
* **Case 3 (Connect RPC IS open, but the query DOES NOT HAVE an active subscription)**: Send an `Execute` request over the existing `Connect` RPC stream and serve the response.
  * *Rationale*: Reuses the active stream for the oneshot request to avoid the handshake overhead of a new unary connection.

Furthermore, `MutationRefImpl` will eventually be updated to follow the same opportunistic routing logic—routing mutations over the active `Connect` RPC stream if available.

## Options Considered

* **Option A: Unified `OperationManager` immediately**
  * *Pros:* Cleaner architecture from day one; avoids temporary "hacks" and potential race conditions in `QuerySubscriptionImpl`.
  * *Cons:* Extremely high complexity and risk. Tying unary request routing into the bidirectional stream connection pool requires major refactoring of the network layer, which would delay the graduation of the stable realtime feature.
  * *Reason for Rejection:* The timeline and stability requirements of graduating Realtime Queries in version 17.3.0 required a lower-risk, incremental path. The hybrid bridge allowed us to stabilize the bidirectional stream multiplexing first.
* **Option B: Realtime-only subscriptions**
  * *Pros:* Simple implementation; `QuerySubscriptionImpl` only listens to `RealtimeQueryManager`.
  * *Cons:* Breaks backwards compatibility. Users who call `QueryRef.execute()` would no longer see their active subscriptions update locally unless the server also pushed an update.
  * *Reason for Rejection:* Maintaining consistent local cache updates is a core requirement of the SDK.

## Consequences

* **Positive:**
  * Allows immediate graduation of Realtime Queries in version 17.3.0 with full backwards compatibility for local query executions.
  * Unblocks progress without requiring a massive, high-risk refactoring of unary RPC routing.
* **Negative/Risks:**
  * **Out-of-Order Updates (Staleness Risk)**: Because the two streams emit updates independently, there is a race condition risk. If a slow local cache read emits *after* a fresh server-initiated push update, the stale cache data could clobber the fresher server data downstream.
  * **Added Complexity**: `QuerySubscriptionImpl.flow` is more complex, requiring parallel coroutine jobs (`nonRealtimeJob` launched in a `channelFlow`) and custom cleanup logic to avoid leaking subscriptions.

