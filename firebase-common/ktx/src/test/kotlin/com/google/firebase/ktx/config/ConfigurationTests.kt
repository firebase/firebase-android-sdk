package com.google.firebase.ktx.config

import com.google.common.truth.Truth.assertThat
import com.google.firebase.components.Qualified
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

interface SomeConfig {
  var isSomeEnabled: Boolean
  fun useFoo(foo: String)
}

private data class SomeConfigImpl(
  override var isSomeEnabled: Boolean = true,
  var foo: String? = null
) : ConfigValue, SomeConfig {
  override fun useFoo(foo: String) {
    this.foo = foo
  }
}

private fun ConfigurationDsl.someConfig(block: SomeConfig.() -> Unit) {
  internalGetForConfigure(this, SomeConfigImpl::class.java) { SomeConfigImpl() }.also { it.block() }
}

@RunWith(RobolectricTestRunner::class)
class ConfigurationTests {
  @Test
  fun foo() {
    val config = firebase {
      someConfig { isSomeEnabled = false }
      someConfig { useFoo("Hello") }
    }

    assertThat(config.configs).hasSize(1)
    val component = config.configs[0]
    assertThat(component.providedInterfaces)
      .containsExactly(Qualified.unqualified(SomeConfigImpl::class.java))
    val value = component.factory.create(null)
    assertThat(value).isEqualTo(SomeConfigImpl(false, "Hello"))
  }
}
