package com.google.firebase.database.ktx

import com.google.firebase.database.DataSnapshot

/**
 * Used to emit events about changes in the child locations of a given [Query] when using the
 * [childEvents] Flow.
 */
sealed class ChildEvent {
  /**
   * Emitted when a new child is added to the location.
   *
   * @param snapshot An immutable snapshot of the data at the new child location
   * @param previousChildName The key name of sibling location ordered before the new child. This
   * ```
   *     will be null for the first child node of a location.
   * ```
   */
  data class Added(val snapshot: DataSnapshot, val previousChildName: String?) : ChildEvent()

  /**
   * Emitted when the data at a child location has changed.
   *
   * @param snapshot An immutable snapshot of the data at the new data at the child location
   * @param previousChildName The key name of sibling location ordered before the child. This will
   * ```
   *     be null for the first child node of a location.
   * ```
   */
  data class Changed(val snapshot: DataSnapshot, val previousChildName: String?) : ChildEvent()

  /**
   * Emitted when a child is removed from the location.
   *
   * @param snapshot An immutable snapshot of the data at the child that was removed.
   */
  data class Removed(val snapshot: DataSnapshot) : ChildEvent()

  /**
   * Emitted when a child location's priority changes.
   *
   * @param snapshot An immutable snapshot of the data at the location that moved.
   * @param previousChildName The key name of the sibling location ordered before the child
   * ```
   *     location. This will be null if this location is ordered first.
   * ```
   */
  data class Moved(val snapshot: DataSnapshot, val previousChildName: String?) : ChildEvent()
}
