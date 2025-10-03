// Copyright 2025 Google LLC
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

package com.google.firebase.database

import com.google.firebase.database.core.view.Event
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class EventHelperKt : AutoCloseable {

  private val stateLock = ReentrantLock()
  private val stateCondition = stateLock.newCondition()
  private var state: State = State.Building(expectations = emptyList())

  private val timeout: Duration
    get() = IntegrationTestValues.getTimeout().milliseconds

  private open class Expectation(
    val eventType: Event.EventType,
    val ref: DatabaseReference,
  ) {

    open fun matches(record: EventRecord): Boolean =
      record.eventType == eventType && record.snapshot.ref.toString() == ref.toString()

    override fun toString(): String = "$eventType => $ref"
  }

  private class ValueExpectation(
    eventType: Event.EventType,
    ref: DatabaseReference,
    val expectedValue: Any,
  ) : Expectation(eventType, ref) {

    override fun matches(record: EventRecord): Boolean =
      super.matches(record) && record.snapshot.getValue(expectedValue::class.java) == expectedValue

    override fun toString(): String = "$eventType => $ref (expectedValue=$expectedValue)"
  }

  fun addValueExpectation(ref: DatabaseReference): EventHelperKt =
    addExpectation(Expectation(Event.EventType.VALUE, ref))

  fun addValueExpectation(ref: DatabaseReference, expectedValue: Any): EventHelperKt =
    addExpectation(ValueExpectation(Event.EventType.VALUE, ref, expectedValue))

  fun addChildExpectation(
    ref: DatabaseReference,
    eventType: Event.EventType,
    childName: String
  ): EventHelperKt = addExpectation(Expectation(eventType, ref.child(childName)))

  private fun addExpectation(expectation: Expectation): EventHelperKt = apply {
    updateState<State.Building> { currentState ->
      val newExpectations = currentState.expectations.toMutableList()
      newExpectations.add(expectation)
      currentState.copy(newExpectations)
    }
  }

  private inline fun <reified T : State> updateState(block: (currentState: T) -> State) {
    getAndUpdateState(block)
  }

  private inline fun <reified T : State> getAndUpdateState(block: (currentState: T) -> State): T =
    stateLock.withLock {
      val oldState = state
      if (oldState !is T) {
        throw IllegalStateException("unexpected state: $oldState")
      }
      state = block(oldState)
      if (state !== oldState) {
        stateCondition.signalAll()
      }
      oldState
    }

  fun startListening(): EventHelperKt = startListening(false)

  fun startListening(waitForInitialization: Boolean): EventHelperKt = apply {
    val oldState =
      getAndUpdateState<State.Building> { currentState ->
        State.Listening(currentState.expectations, emptyList(), emptyList(), emptyList())
      }

    val refsSortedByLength =
      oldState.expectations
        .map { it.ref }
        .distinctBy { it.toString() }
        .sortedBy { it.toString().length }

    fun recordEvent(snapshot: DataSnapshot, eventType: Event.EventType, previousChild: String?) {
      val event = EventRecord(snapshot, eventType, previousChild)
      updateState<State> { currentState ->
        if (currentState !is State.Listening) {
          currentState
        } else {
          val newEvents = currentState.events.toMutableList()
          newEvents.add(event)
          currentState.copy(events = newEvents)
        }
      }
    }

    for (ref in refsSortedByLength) {
      val valueEventListener =
        ref.addValueEventListener(
          object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
              recordEvent(snapshot, Event.EventType.VALUE, null)
            }

            override fun onCancelled(error: DatabaseError) {
              // No-op
            }
          }
        )

      val childEventListener =
        ref.addChildEventListener(
          object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
              recordEvent(snapshot, Event.EventType.CHILD_ADDED, previousChildName)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
              recordEvent(snapshot, Event.EventType.CHILD_CHANGED, previousChildName)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
              recordEvent(snapshot, Event.EventType.CHILD_REMOVED, null)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
              recordEvent(snapshot, Event.EventType.CHILD_MOVED, previousChildName)
            }

            override fun onCancelled(error: DatabaseError) {
              // No-op
            }
          }
        )

      val oldState =
        getAndUpdateState<State> { currentState ->
          if (currentState !is State.Listening) {
            currentState
          } else {
            val newValueEventListeners = currentState.valueEventListeners.toMutableList()
            val valueEventListenerRegistration =
              State.Listening.ValueEventListenerRegistration(ref, valueEventListener)
            newValueEventListeners.add(valueEventListenerRegistration)

            val newChildEventListeners = currentState.childEventListeners.toMutableList()
            val childEventListenerRegistration =
              State.Listening.ChildEventListenerRegistration(ref, childEventListener)
            newChildEventListeners.add(childEventListenerRegistration)
            currentState.copy(
              valueEventListeners = newValueEventListeners,
              childEventListeners = newChildEventListeners
            )
          }
        }

      if (oldState !is State.Listening) {
        ref.removeEventListener(valueEventListener)
        ref.removeEventListener(childEventListener)
        throw IllegalStateException("unexpected state: $oldState")
      }
    }

    if (waitForInitialization) {
      waitForInitialization()
    }
  }

  private fun waitForInitialization() = waitForEvents { listeningState ->
    val uninitializedLocations =
      listeningState.expectations.map { it.ref.toString() }.toMutableSet()

    val lastInitializedIndex =
      listeningState.events.indexOfFirst { eventRecord ->
        uninitializedLocations.remove(eventRecord.snapshot.ref.toString())
        uninitializedLocations.isEmpty()
      }

    if (lastInitializedIndex < 0) {
      WaitForEventsIterationResult.KeepWaiting
    } else {
      val newEvents = listeningState.events.toMutableList()
      newEvents.subList(0, lastInitializedIndex + 1).clear()
      state = listeningState.copy(events = newEvents)
      WaitForEventsIterationResult.Done
    }
  }

  fun waitForEventsThatFulfillExpectationsOrThrow() = waitForEvents { listeningState ->
    val remainingExpectations = listeningState.expectations.toMutableList()
    if (remainingExpectations.isEmpty()) {
      throw IllegalStateException("waitForEvent() called with no expectations")
    }

    listeningState.events.forEach { eventRecord ->
      val expectationIndex = remainingExpectations.indexOfFirst { it.matches(eventRecord) }
      if (expectationIndex >= 0) {
        remainingExpectations.removeAt(expectationIndex)
      }
    }

    if (remainingExpectations.isEmpty()) {
      WaitForEventsIterationResult.Done
    } else {
      WaitForEventsIterationResult.KeepWaiting
    }
  }

  private enum class WaitForEventsIterationResult {
    KeepWaiting,
    Done,
  }

  private inline fun waitForEvents(block: (State.Listening) -> WaitForEventsIterationResult) {
    val endTimeMs = System.nanoTime().nanoseconds.inWholeMilliseconds + timeout.inWholeMilliseconds
    while (true) {
      stateLock.withLock {
        val listeningState = state
        if (listeningState !is State.Listening) {
          throw IllegalStateException("unexpected state: $listeningState")
        }

        when (block(listeningState)) {
          WaitForEventsIterationResult.KeepWaiting -> {}
          WaitForEventsIterationResult.Done -> return
        }

        val millisRemaining = endTimeMs - System.nanoTime().nanoseconds.inWholeMilliseconds
        if (millisRemaining < 0) {
          throw Exception("timeout (${timeout.inWholeMilliseconds} milliseconds)")
        }
        stateCondition.await(millisRemaining, TimeUnit.MILLISECONDS)
      }
    }
  }

  override fun close() {
    updateState<State> { currentState ->
      if (currentState is State.Listening) {
        currentState.run {
          valueEventListeners.forEach { it.ref.removeEventListener(it.listener) }
          childEventListeners.forEach { it.ref.removeEventListener(it.listener) }
        }
      }
      State.Closed
    }
  }

  private sealed interface State {
    data class Building(val expectations: List<Expectation>) : State

    data class Listening(
      val expectations: List<Expectation>,
      val events: List<EventRecord>,
      val valueEventListeners: List<ValueEventListenerRegistration>,
      val childEventListeners: List<ChildEventListenerRegistration>,
    ) : State {
      data class ValueEventListenerRegistration(
        val ref: DatabaseReference,
        val listener: ValueEventListener
      )
      data class ChildEventListenerRegistration(
        val ref: DatabaseReference,
        val listener: ChildEventListener
      )
    }

    object Closed : State
  }
}
