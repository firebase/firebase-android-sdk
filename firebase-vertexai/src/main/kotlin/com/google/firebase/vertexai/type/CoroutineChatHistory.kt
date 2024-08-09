package com.google.firebase.vertexai.type

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An async alternative to [BaseChatHistory]
 * to be used in cases where writing/reading messages
 * requires expensive operations such as reading from disk
 * or fetching/sending values over the network.
 */
interface CoroutineChatHistory {

  /**
   * Flow containing the messages from history
   */
  val history: MutableStateFlow<Content>

  /**
   * Adds messages to the list
   */
  suspend fun addMessages(message: Content, vararg messages: Content)

  /**
   * Clears all messages from history
   */
  suspend fun clearMessages()
}