---

id: 0003
title: Sequence Number Filtering for Stale Replayed Data
date: Sun May 17 00:02:50 EDT 2026
status: accepted
deciders: [Jetski, dconeybe]
tags: [network, grpc, coroutines, flow, multiplexing]
-----------------------------------------------------

# Sequence Number Filtering for Stale Replayed Data

## Context

When utilizing multiplexing on the bidirectional gRPC stream, multiple concurrent queries sharing the same request ID (e.g., queries targeting the same document path or query parameters) subscribe to the same gRPC flow. To support late subscribers and avoid redundant network round-trips, the `sharedFlow` uses `replay = 1`.

However, this means when a second subscriber starts collecting from the `sharedFlow`, they immediately receive the last emitted event from the replay cache. This event might be `Event.Message` containing data from a previous subscription request (`testData1`).

While `replayExpirationMillis = 0` ensures that the replay cache is cleared when the subscriber count drops to `0` (as decided in ADR 0002), it does NOT solve the issue when a *new* subscriber joins while there is *already* at least one active subscriber. In this concurrent scenario, the replay cache still contains the last message sent to the existing subscriber.

If a new subscriber starts collecting, they receive the replayed `Event.Message` from the cache. This is stale data for the new subscriber because they want to receive the result of their *own* subscription (or the latest fresh state), not a potentially stale message that was already delivered in the past.

To mitigate this, we introduced a **sequence number** in each `Event.Message` emitted by the stream. When a subscriber starts collecting, it records the current sequence number (`flowCollectorStartSequenceNumber`). The subscriber then discards any incoming `Event.Message` whose sequence number is strictly less than `flowCollectorStartSequenceNumber`.

This works perfectly for discarding stale *replayed* messages already residing in the `sharedFlow`'s replay cache prior to the new subscriber starting.

However, there is a critical race condition where sequence numbers are not sufficient: **in-flight subscription requests**.

## The In-Flight Request Issue

Consider the following sequence of events:
1. **Subscriber 1** starts collecting. It triggers a `subscribe` request to the server. The server has not yet responded.
2. **Subscriber 2** starts collecting. Because the connection is already active (subscriber count is > 0), it shares the same connection. It records its `flowCollectorStartSequenceNumber` (e.g., `3`).
3. The server now responds to **Subscriber 1**'s initial `subscribe` request. The client receives this message, assigns it a sequence number (e.g., `4`), and emits it.
4. Because the sequence number of the message (`4`) is greater than or equal to **Subscriber 2**'s start sequence number (`3`), the filter allows the message to pass.
5. Consequently, **Subscriber 2** incorrectly receives the data intended for **Subscriber 1** (`testData1`), instead of waiting for the response to its own subscription request (`testData2`).

### Why Sequence Numbers Cannot Fix This

The client-side sequence number filter cannot physically distinguish between `testData1` (the response to Subscriber 1's request) and `testData2` (the response to Subscriber 2's request) once they arrive, because:
1. Both subscribers share the same `requestId` since they are multiplexing the same query on the same bidirectional stream.
2. The underlying `StreamResponse` protocol does not include a request-response correlation token or transaction/acknowledgement ID echoed by the server that would allow the client to match a specific `StreamResponse` to a specific `StreamRequest`.
3. As a result, any message received on the stream with the correct `requestId` looks identical to the client, regardless of which client-side subscription request actually triggered it. If a message arrives *after* a subscriber has started collecting, and its sequence number is therefore higher than the subscriber's start sequence number, the client must assume it is a valid update.

## Decision

We accept the sequence number filtering approach as an effective solution for discarding stale *replayed* data from the replay cache (messages that arrived *before* the new subscriber started).

We acknowledge that sequence number filtering **does not** solve the race condition for in-flight subscription requests (messages that arrive *after* the new subscriber starts but were triggered by a previous subscriber's request).

We will leave the in-flight subscription request issue as a known limitation for now, to be resolved by a separate protocol or client-side state tracking mechanism.

## Rationale

The sequence number filter is extremely lightweight and completely solves the "stale replay cache" bug for late subscribers joining when the stream is idle or when they join after a message has already been fully processed.

Attempting to solve the in-flight issue purely within the sequence number mechanism is mathematically impossible due to the lack of message correlation in the protocol. Any attempt to solve it without modifying the protocol or adding complex request tracking would result in overly fragile code.

## Proposed Mitigation (Future Work)

A potential client-side mitigation that builds upon the sequence number approach involves:
1. Keeping track of whether a `subscribe` or `resume` request sent to the server has received a corresponding message response.
2. Delaying the transmission of a subsequent `resume` or `subscribe` request from a new subscriber until the outstanding request from the previous subscriber has received an "acknowledgement" (i.e., the corresponding data message).
3. This effectively serializes the subscription lifecycle on the multiplexed stream, ensuring that the response to Subscriber 1's request is received and processed before Subscriber 2 initiates its subscription sequence, avoiding the overlapping in-flight race condition.

## Options Considered

* **Option A: Keep Sequence Number Filtering and Accept In-Flight Limitation** (This Decision)
  * *Pros:* Simplicity. Solves the primary stale-replay bug. No complex state synchronization required.
  * *Cons:* In-flight race condition remains unresolved (concurrently starting subscribers might briefly see the previous subscriber's in-flight response before getting their own).
* **Option B: Complex Protocol/Correlation Token Changes**
  * *Pros:* Completely solves both stale-replay and in-flight issues with absolute certainty.
  * *Cons:* Requires server-side protocol changes to echo back correlation tokens in `StreamResponse`. Not feasible for a pure client-side library fix.
* **Option C: Fully Revert to CompletableDeferred/Replay=0**
  * *Pros:* None (previously rejected).
  * *Cons:* Reintroduced extreme complexity and was rejected due to defeating the elegance of `WhileSubscribed`.

## Consequences

* **Positive:**
  * Robust protection against processing stale data from the flow's replay cache.
  * Zero performance overhead.
  * Clear documentation of the protocol's limitations.
* **Negative/Risks:**
  * A late subscriber that starts collecting *exactly* in the small window between another subscriber's request and its response will still receive that in-flight response. This is mitigated by the fact that the data is still technically the "latest" data, but in unit tests asserting strict deterministic sequence of distinct datasets, this causes test failures.

