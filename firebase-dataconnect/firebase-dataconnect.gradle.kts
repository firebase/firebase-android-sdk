/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("firebase-library")
  id("kotlin-android")
  id("com.google.protobuf")
  id("copy-google-services")
  alias(libs.plugins.kotlinx.serialization)
}

firebaseLibrary {
  libraryGroup = "dataconnect"
  testLab.enabled = false
  publishJavadoc = false
  previewMode = "beta"
  releaseNotes {
    name.set("{{data_connect_short}}")
    versionName.set("data-connect")
    hasKTX.set(false)
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }

  @Suppress("UnstableApiUsage")
  testOptions {
    targetSdk = targetSdkVersion
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  lint { targetSdk = targetSdkVersion }

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }
}

protobuf {
  protoc { artifact = "${libs.protoc.get()}" }
  plugins {
    create("java") { artifact = "${libs.grpc.protoc.gen.java.get()}" }
    create("grpc") { artifact = "${libs.grpc.protoc.gen.java.get()}" }
    create("grpckt") { artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar" }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins { create("kotlin") { option("lite") } }
      task.plugins {
        create("java") { option("lite") }
        create("grpc") { option("lite") }
        create("grpckt") { option("lite") }
      }
    }
  }
}

dependencies {
  api("com.google.firebase:firebase-common:21.0.0")

  implementation("com.google.firebase:firebase-annotations:16.2.0")
  implementation("com.google.firebase:firebase-appcheck-interop:17.1.0")
  implementation("com.google.firebase:firebase-auth-interop:20.0.0")
  implementation("com.google.firebase:firebase-components:18.0.0")

  compileOnly(libs.javax.annotation.jsr250)
  compileOnly(libs.kotlinx.datetime)
  implementation(libs.grpc.android)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.stub)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.protobuf.java.lite)
  implementation(libs.protobuf.kotlin.lite)

  testCompileOnly(libs.protobuf.java)
  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.property)
  testImplementation(libs.kotest.property.arbs)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.kotlinx.datetime)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.mockk)
  testImplementation(libs.testonly.three.ten.abp)
  testImplementation(libs.robolectric)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:connectors"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  androidTestImplementation("com.google.firebase:firebase-appcheck:18.0.0")
  androidTestImplementation("com.google.firebase:firebase-auth:22.3.1")
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotest.assertions)
  androidTestImplementation(libs.kotest.property)
  androidTestImplementation(libs.kotest.property.arbs)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.kotlinx.datetime)
  androidTestImplementation(libs.mockk)
  androidTestImplementation(libs.mockk.android)
  androidTestImplementation(libs.testonly.three.ten.abp)
  androidTestImplementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions { freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn") }
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinCompile>().all {
  if (!name.contains("test", ignoreCase = true)) {
    if (!kotlinOptions.freeCompilerArgs.contains("-Xexplicit-api=strict")) {
      kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}

/**
 * Performs various transformations on a mutable list of strings. This class is _not_ thread safe;
 * any concurrent use must be synchronized externally or else the behavior is undefined.
 * @property lines the lines to mutate; this list is modified in-place.
 */
class TextLinesTransformer(val lines: MutableList<String>) {
  constructor(lines: Iterable<String>) : this(lines.toMutableList())

  fun indexOf(predicateDescription: String, predicate: (String) -> Boolean): Int {
    val index = lines.indexOfFirst(predicate)
    if (index < 0) {
      throw TextLinesTransformerException("unable to find a line that $predicateDescription")
    }
    return index
  }

  fun atLineThatStartsWith(prefix: String): IndexBasedOperations {
    val index = indexOf("starts with \"$prefix\"") { it.startsWith(prefix) }
    return IndexBasedOperations(index)
  }

  fun removeLine(line: String) {
    lines.removeAll { it.trim() == line }
  }

  fun replaceLine(line: String, replacementLine: String) {
    lines.replaceAll { originalLine -> originalLine.takeIf { it != line } ?: replacementLine }
  }

  fun replaceWord(
    original: String,
    replacement: String,
    predicate: (line: String) -> Boolean = { true }
  ) {
    val regex = Regex("""(\W|^)${Regex.escape(original)}(\W|$)""")
    lines.replaceAll { line ->
      if (!predicate(line)) {
        line
      } else {
        regex.replace(line) { matchResult ->
          val prefix = matchResult.groupValues[1]
          val suffix = matchResult.groupValues[2]
          "$prefix${Regex.escapeReplacement(replacement)}$suffix"
        }
      }
    }
  }

  fun replaceText(original: String, replacement: String) {
    lines.replaceAll { it.replace(original, replacement) }
  }

  fun replaceRegex(pattern: String, replacement: String) {
    val regex = Regex(pattern)
    lines.replaceAll { regex.replace(it, replacement) }
  }

  fun applyReplacements(linesByReplacementId: Map<String, List<String>>) {
    for (index in lines.indices.reversed()) {
      val line = lines[index]
      val matchResult = replacementsRegex.matchEntire(line.trim()) ?: continue
      val lineDeleteCount = matchResult.groupValues[1].toInt() + 1
      val replacementId = matchResult.groupValues[2]

      val replacementLines =
        linesByReplacementId[replacementId]
          ?: throw Exception(
            "Replacement ID \"$replacementId\" is not known; " +
              "there are ${linesByReplacementId.size} known replacementIds: " +
              linesByReplacementId.keys.sorted().joinToString(", ") +
              " (error code zgcc257b23)"
          )

      repeat(lineDeleteCount) { lines.removeAt(index) }
      lines.addAll(index, replacementLines)
    }
  }

  inner class IndexBasedOperations(private var index: Int) {
    fun deleteLinesAboveThatStartWith(prefix: String): IndexBasedOperations = apply {
      while (lines[index - 1].startsWith(prefix)) {
        lines.removeAt(index - 1)
        index--
      }
    }

    fun insertAbove(line: String): IndexBasedOperations = apply { lines.add(index, line) }

    fun insertAbove(lines: Collection<String>): IndexBasedOperations = apply {
      this@TextLinesTransformer.lines.addAll(index, lines)
    }
  }

  private class TextLinesTransformerException(message: String) : Exception(message)

  companion object {
    private val replacementsRegex: Regex = run {
      fun StringBuilder.appendRegexEscaped(s: String) = append(Regex.escape(s))
      val pattern = buildString {
        appendRegexEscaped("//")
        append("""\s*""")
        appendRegexEscaped("""ReplaceLinesBelow(numLines=""")
        append("""\s*(\d+)\s*,\s*""")
        appendRegexEscaped("""replacementId=""")
        append("""(\w+)""")
        appendRegexEscaped(""")""")
      }
      Regex(pattern)
    }

    fun getGeneratedFileWarningLines(srcFile: File) =
      listOf(
        "/".repeat(80),
        "// WARNING: THIS FILE IS GENERATED FROM ${srcFile.name}",
        "// DO NOT MODIFY THIS FILE BY HAND BECAUSE MANUAL CHANGES WILL GET OVERWRITTEN",
        "// THE NEXT TIME THAT THIS FILE IS REGENERATED. TO REGENERATE THIS FILE, RUN:",
        "// ./gradlew :firebase-dataconnect:generateDataConnectTestingSources",
        "/".repeat(80),
      )
  }
}

fun generateLocalDateSerializerUnitTest(
  srcFile: File,
  classNameUnderTest: String,
  localDateFullyQualifiedClassName: String,
  localDateFactoryCall: String,
  logger: Logger,
) {
  logger.info("Reading {}", srcFile.absolutePath)
  val transformer = TextLinesTransformer(srcFile.readLines(Charsets.UTF_8))

  val linesByReplacementId =
    mapOf(
      "CoerceDayOfMonthIntoValidRangeFor" to
        listOf(
          "fun Int.coerceDayOfMonthIntoValidRangeFor(month: Int, year: Int): Int {",
          "  val monthObject = org.threeten.bp.Month.of(month)",
          "  val yearObject = org.threeten.bp.Year.of(year)",
          "  val dayRange = monthObject.dayRangeInYear(yearObject)",
          "  return coerceIn(dayRange)",
          "}",
        ),
      "LocalDateSample" to
        listOf(
          "val coercedDayInt = dayInt.coerceDayOfMonthIntoValidRangeFor(month=monthInt, year=yearInt)",
          "$localDateFullyQualifiedClassName$localDateFactoryCall(yearInt, monthInt, coercedDayInt)",
        ),
    )

  val generatedFileWarningLines = TextLinesTransformer.getGeneratedFileWarningLines(srcFile)

  transformer.run {
    atLineThatStartsWith("import ")
      .insertAbove("import com.google.firebase.dataconnect.testutil.dayRangeInYear")
    removeLine("import com.google.firebase.dataconnect.LocalDate")
    replaceWord("LocalDate", localDateFullyQualifiedClassName) { !it.contains('`') }
    replaceText("LocalDateSerializer", classNameUnderTest)
    replaceText(
      "val monthString = month.toZeroPaddedString(monthPadding)",
      "val monthString = month.value.toZeroPaddedString(monthPadding)"
    )
    replaceText(
      "val dayString = day.toZeroPaddedString(dayPadding)",
      "val dayString = dayOfMonth.toZeroPaddedString(dayPadding)"
    )
    replaceText(
      "year: Arb<Int> = intWithEvenNumDigitsDistribution()",
      "year: Arb<Int> = intWithEvenNumDigitsDistribution(java.time.Year.MIN_VALUE..java.time.Year.MAX_VALUE)"
    )
    replaceText(
      "month: Arb<Int> = intWithEvenNumDigitsDistribution()",
      "month: Arb<Int> = intWithEvenNumDigitsDistribution(1..12)"
    )
    replaceText(
      "day: Arb<Int> = intWithEvenNumDigitsDistribution()",
      "day: Arb<Int> = intWithEvenNumDigitsDistribution(1..31)"
    )
    applyReplacements(linesByReplacementId)

    atLineThatStartsWith("package ")
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)

    atLineThatStartsWith("class ")
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)
  }

  val destFile = File(srcFile.parentFile, "${classNameUnderTest}UnitTest.kt")
  logger.info("Writing {}", destFile.absolutePath)
  destFile.writeText(transformer.lines.joinToString("\n"))
}

fun generateLocalDateSerializerIntegrationTest(
  srcFile: File,
  destClassName: String,
  localDateFullyQualifiedClassName: String,
  localDateFactoryCall: String,
  convertFromDataConnectLocalDateFunctionName: String,
  serializerClassName: String,
  logger: Logger,
) {
  logger.info("Reading {}", srcFile.absolutePath)
  val transformer = TextLinesTransformer(srcFile.readLines(Charsets.UTF_8))

  val generatedFileWarningLines = TextLinesTransformer.getGeneratedFileWarningLines(srcFile)

  transformer.run {
    removeLine("import com.google.firebase.dataconnect.LocalDate")
    replaceLine(
      "@file:UseSerializers(UUIDSerializer::class)",
      "@file:UseSerializers(UUIDSerializer::class, $serializerClassName::class)"
    )
    atLineThatStartsWith("import ")
      .insertAbove("import com.google.firebase.dataconnect.serializers.$serializerClassName")
      .insertAbove("import io.kotest.property.arbitrary.map")
    replaceWord("LocalDate", localDateFullyQualifiedClassName) { !it.contains('`') }
    replaceWord("LocalDateIntegrationTest", destClassName)
    replaceWord(
      "Arb.dataConnect.localDate()",
      "Arb.dataConnect.localDate().map{it.$convertFromDataConnectLocalDateFunctionName()}"
    )
    replaceRegex("""\?\.date(\W|$)""", "?.date?.$convertFromDataConnectLocalDateFunctionName()$1")
    replaceRegex(
      """([^?])\.date(\W|${'$'})""",
      "$1.date.$convertFromDataConnectLocalDateFunctionName()$2"
    )
    replaceText(
      "$localDateFullyQualifiedClassName(",
      "$localDateFullyQualifiedClassName$localDateFactoryCall("
    )

    atLineThatStartsWith("package ")
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)

    atLineThatStartsWith("class ")
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)
  }

  val destFile = File(srcFile.parentFile, "$destClassName.kt")
  logger.info("Writing {}", destFile.absolutePath)
  destFile.writeText(transformer.lines.joinToString("\n"))
}

tasks.register("generateDataConnectUnitTestingSources") {
  val dir = file("src/test/kotlin/com/google/firebase/dataconnect/serializers")
  val srcFile = File(dir, "LocalDateSerializerUnitTest.kt")
  doLast {
    generateLocalDateSerializerUnitTest(
      srcFile = srcFile,
      classNameUnderTest = "JavaTimeLocalDateSerializer",
      localDateFullyQualifiedClassName = "java.time.LocalDate",
      localDateFactoryCall = ".of",
      logger = logger,
    )
    generateLocalDateSerializerUnitTest(
      srcFile = srcFile,
      classNameUnderTest = "KotlinxDatetimeLocalDateSerializer",
      localDateFullyQualifiedClassName = "kotlinx.datetime.LocalDate",
      localDateFactoryCall = "",
      logger = logger,
    )
  }
}

tasks.register("generateDataConnectIntegrationTestingSources") {
  val dir = file("src/androidTest/kotlin/com/google/firebase/dataconnect")
  val srcFile = File(dir, "LocalDateIntegrationTest.kt")
  doLast {
    generateLocalDateSerializerIntegrationTest(
      srcFile = srcFile,
      destClassName = "JavaTimeLocalDateIntegrationTest",
      localDateFullyQualifiedClassName = "java.time.LocalDate",
      localDateFactoryCall = ".of",
      convertFromDataConnectLocalDateFunctionName = "toJavaLocalDate",
      serializerClassName = "JavaTimeLocalDateSerializer",
      logger = logger,
    )
    generateLocalDateSerializerIntegrationTest(
      srcFile = srcFile,
      destClassName = "KotlinxDatetimeLocalDateIntegrationTest",
      localDateFullyQualifiedClassName = "kotlinx.datetime.LocalDate",
      localDateFactoryCall = "",
      convertFromDataConnectLocalDateFunctionName = "toKotlinxLocalDate",
      serializerClassName = "KotlinxDatetimeLocalDateSerializer",
      logger = logger,
    )
  }
}

tasks.register("generateDataConnectTestingSources") {
  dependsOn("generateDataConnectUnitTestingSources")
  dependsOn("generateDataConnectIntegrationTestingSources")
}
