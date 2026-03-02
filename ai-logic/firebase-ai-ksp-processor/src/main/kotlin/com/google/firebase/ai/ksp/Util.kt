/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ai.ksp

import com.google.devtools.ksp.symbol.KSAnnotation

// This regex extracts everything in the kdocs until it hits either the end of the kdocs, or
// the first @<tag> like @property or @see, extracting the main body text of the kdoc
private val baseKdocRegex = Regex("""^\s*(.*?)((@\w* .*)|\z)""", RegexOption.DOT_MATCHES_ALL)
// This regex extracts two capture groups from @property tags, the first is the name of the
// property, and the second is the documentation associated with that property
private val propertyKdocRegex =
  Regex("""\s*@property (\w*) (.*?)(?=@\w*|\z)""", RegexOption.DOT_MATCHES_ALL)

internal data class GuideValues(
  val minimum: Double?,
  val maximum: Double?,
  val minItems: Int?,
  val maxItems: Int?,
  val format: String?,
  val description: String?
)

internal fun getGuideValuesFromAnnotation(
  guideAnnotation: KSAnnotation?,
  description: String?
): GuideValues =
  GuideValues(
    minimum = getDoubleFromAnnotation(guideAnnotation, "minimum"),
    maximum = getDoubleFromAnnotation(guideAnnotation, "maximum"),
    minItems = getIntFromAnnotation(guideAnnotation, "minItems"),
    maxItems = getIntFromAnnotation(guideAnnotation, "maxItems"),
    format = getStringFromAnnotation(guideAnnotation, "format"),
    description = description
  )

internal fun getDescriptionFromAnnotations(
  guideAnnotation: KSAnnotation?,
  generableClassAnnotation: KSAnnotation?,
  description: String?,
  baseKdoc: String?,
): String? {
  val guidePropertyDescription = getStringFromAnnotation(guideAnnotation, "description")

  val guideClassDescription = getStringFromAnnotation(generableClassAnnotation, "description")

  return guidePropertyDescription ?: guideClassDescription ?: description ?: baseKdoc
}

internal fun getDoubleFromAnnotation(
  guideAnnotation: KSAnnotation?,
  doubleName: String,
): Double? {
  val guidePropertyDoubleValue =
    guideAnnotation
      ?.arguments
      ?.firstOrNull { it.name?.getShortName()?.equals(doubleName) == true }
      ?.value as? Double
  if (guidePropertyDoubleValue == null || guidePropertyDoubleValue == -1.0) {
    return null
  }
  return guidePropertyDoubleValue
}

internal fun getIntFromAnnotation(guideAnnotation: KSAnnotation?, intName: String): Int? {
  val guidePropertyIntValue =
    guideAnnotation
      ?.arguments
      ?.firstOrNull { it.name?.getShortName()?.equals(intName) == true }
      ?.value as? Int
  if (guidePropertyIntValue == null || guidePropertyIntValue == -1) {
    return null
  }
  return guidePropertyIntValue
}

internal fun getStringFromAnnotation(
  guideAnnotation: KSAnnotation?,
  stringName: String,
): String? {
  val guidePropertyStringValue =
    guideAnnotation
      ?.arguments
      ?.firstOrNull { it.name?.getShortName()?.equals(stringName) == true }
      ?.value as? String
  if (guidePropertyStringValue.isNullOrEmpty()) {
    return null
  }
  return guidePropertyStringValue
}

internal fun extractBaseKdoc(kdoc: String): String? {
  return baseKdocRegex.matchEntire(kdoc)?.groups?.get(1)?.value?.trim().let {
    if (it.isNullOrEmpty()) null else it
  }
}

internal fun extractPropertyKdocs(kdoc: String): Map<String, String> {
  return propertyKdocRegex
    .findAll(kdoc)
    .map { it.groups[1]!!.value to it.groups[2]!!.value.replace("\n", "").trim() }
    .toMap()
}
