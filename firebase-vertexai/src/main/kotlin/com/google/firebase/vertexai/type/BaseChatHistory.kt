package com.google.firebase.vertexai.type

interface BaseChatHistory {

  /**
   * Returns the messages from history as a list
   */
  val history: MutableList<Content>

  /**
   * Adds messages to the list
   */
  fun addMessages(message: Content, vararg messages: Content)

  /**
   * Clears all messages from history
   */
  fun clearMessages()
}

class InMemoryChatHistory : BaseChatHistory {

  override val history: MutableList<Content> = mutableListOf()

  override fun addMessages(message: Content, vararg messages: Content) {
    history.add(message)
    history.addAll(messages)
  }

  override fun clearMessages() {
    history.removeAll(history)
  }

}