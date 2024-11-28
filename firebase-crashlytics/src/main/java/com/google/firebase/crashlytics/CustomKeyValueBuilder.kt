package com.google.firebase.crashlytics

/** Helper class to set custom keys and values. */
open class CustomKeyValueBuilder internal constructor() {
    private val builder = CustomKeysAndValues.Builder()

    internal fun build(): CustomKeysAndValues = builder.build()

    /** Sets a custom key and value that are associated with reports. */
    operator fun set(key: String, value: String) {
        builder.putString(key, value)
    }

    /** Sets a custom key and value that are associated with reports. */
    operator fun set(key: String, value: Double) {
        builder.putDouble(key, value)
    }

    /** Sets a custom key and value that are associated with reports. */
    operator fun set(key: String, value: Float) {
        builder.putFloat(key, value)
    }

    /** Sets a custom key and value that are associated with reports. */
    operator fun set(key: String, value: Int) {
        builder.putInt(key, value)
    }

    /** Sets a custom key and value that are associated with reports. */
    operator fun set(key: String, value: Long) {
        builder.putLong(key, value)
    }

    /** Sets a custom key and value that are associated with reports. */
    operator fun set(key: String, value: Boolean) {
        builder.putBoolean(key, value)
    }

    companion object {
        fun implementation(block: (CustomKeyValueBuilder) -> Unit) {
            val customKeyValueBuilder = CustomKeyValueBuilder()
            block(customKeyValueBuilder)
        }

        fun testImplementation() {
            implementation {
                it["key"] = true
                it["keyFloat"] = 1.01
            }
        }
    }
}