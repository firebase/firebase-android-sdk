package com.google.firebase.gradle

import io.kotest.assertions.assertionCounter
import io.kotest.assertions.failure
import io.kotest.assertions.print.StringPrint
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.string.shouldContain
import io.mockk.MockKMatcherScope
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import kotlin.test.assertEquals

/**
 * Create a [GradleRunner] and run it.
 *
 * @param directory The project directory to run gradle from
 * @param arguments Task arguments to pass to gradle
 * @see createGradleRunner
 */
fun runGradle(directory: File, vararg arguments: String): BuildResult =
  createGradleRunner(directory, *arguments).build()

/**
 * Creates a [GradleRunner], with preconfigured values for tests.
 *
 * @param directory The project directory to run gradle from
 * @param arguments Task arguments to pass to gradle
 * @see runGradle
 */
fun createGradleRunner(directory: File, vararg arguments: String): GradleRunner =
  GradleRunner.create()
    .withProjectDir(directory)
    .withPluginClasspath()
    .forwardOutput()
    .withArguments(
      *arguments,
      "--stacktrace",
      "-Dorg.gradle.kotlin.dsl.scriptCompilationAvoidance=false",
    )

/** Match arguments that end with the specified [str]. */
fun MockKMatcherScope.endsWith(str: String) = match<String> { it.endsWith(str) }

/**
 * Asserts that an exception is thrown with a message that contains the provided [substrings].
 *
 * ```
 * shouldThrowSubstring("fetching", "firebase-common") {
 *   throw RuntimeException("We ran into a problem while fetching the firebase-common artifact")
 * }
 * ```
 *
 * @param substrings A variable amount of strings that the exception message should contain.
 * @param block The callback that should throw the exception.
 */
inline fun shouldThrowSubstring(vararg substrings: String, block: () -> Unit) {
  val exception = shouldThrowAny {
    block()
  }

  for(str in substrings) {
    exception.message.shouldContain(str)
  }
}

/**
 * Asserts that this string is equal to the [expected] string.
 *
 * Should be used in place of [shouldBeEqual] when working with multi-line strings, as this method
 * will provide a proper diff in the console _and_ IDE of which parts of the string were different.
 *
 * Works around [kotest/issues/1084](https://github.com/kotest/kotest/issues/1084).
 *
 * @param expected The string that this string should have the same contents of.
 */
infix fun String.shouldBeDiff(expected: String) = assertEquals(expected, this)