package com.google.firebase.perf.ttid

import android.view.Window

class WindowDelegateCallback constructor(
    private val delegate: Window.Callback
) : Window.Callback by delegate {

    val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

    override fun onContentChanged() {
        onContentChangedCallbacks.removeAll { callback ->
            !callback()
        }
        delegate.onContentChanged()
    }

    companion object {
        fun Window.onDecorViewReady(callback: () -> Unit) {
            if (peekDecorView() == null) {
                onContentChanged {
                    callback()
                    return@onContentChanged false
                }
            } else {
                callback()
            }
        }

        fun Window.onContentChanged(block: () -> Boolean) {
            val callback = wrapCallback()
            callback.onContentChangedCallbacks += block
        }

        private fun Window.wrapCallback(): WindowDelegateCallback {
            val currentCallback = callback
            return if (currentCallback is WindowDelegateCallback) {
                currentCallback
            } else {
                val newCallback = WindowDelegateCallback(currentCallback)
                callback = newCallback
                newCallback
            }
        }
    }
}