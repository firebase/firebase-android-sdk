package com.google.firebase.gradle

import io.kotest.assertions.assertionCounter
import io.kotest.assertions.failure
import io.kotest.assertions.print.StringPrint
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrowAny
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
 * TODO(): Document
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
 * TODO: Document
 *
 * works around https://github.com/kotest/kotest/issues/1084
 */
infix fun String.shouldBeDiff(expected: String) = assertEquals(expected, this)