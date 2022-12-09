package com.google.firebase.ktx.config

import androidx.annotation.RestrictTo
import com.google.firebase.components.Component

class FirebaseConfig internal constructor(internal val configs: List<Component<*>>)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface ConfigValue {
  @Suppress("UNCHECKED_CAST")
  val components: List<Component<*>>
    get() = listOf(Component.of(this, this::class.java as Class<ConfigValue>))
}

interface FirebaseConfigurator {
  fun configure(): FirebaseConfig
}

internal class ConfigHolder<T : ConfigValue>(val value: T, val defaultValue: T)

class ConfigurationDsl internal constructor() {
  private val _configs = mutableMapOf<Class<out ConfigValue>, ConfigHolder<out ConfigValue>>()
  internal fun <T : ConfigValue> getForConfigure(cls: Class<T>, valueCreator: () -> T): T {
    mutableMapOf<Class<ConfigValue>, ConfigValue>()
    if (_configs.contains(cls)) {
      return _configs[cls]!!.value as T
    }
    val newValue = valueCreator()
    _configs[cls] = ConfigHolder(newValue, valueCreator())
    return newValue
  }

  internal val configs: List<Component<*>>
    get() = _configs.values.filter { it.value != it.defaultValue }.flatMap { it.value.components }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun <T : ConfigValue> internalGetForConfigure(
  dsl: ConfigurationDsl,
  cls: Class<T>,
  valueCreator: () -> T
) = dsl.getForConfigure(cls, valueCreator)

fun firebase(configBlock: ConfigurationDsl.() -> Unit): FirebaseConfig {
  val collector = ConfigurationDsl()
  collector.configBlock()
  return FirebaseConfig(collector.configs)
}
