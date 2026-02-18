/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.gradle.plugins

import org.gradle.api.provider.Property

/**
 * Configuration options for release note generation of libraries.
 *
 * Also facilitates the centralization of certain externally defined data (usually defined on G3).
 * As we can't link G3 files on GitHub, if you're setting up a new SDK and are unsure what release
 * note mappings your SDK has (or will have) ask Rachel or the ACore team for the relevant variable
 * mappings.
 *
 * @property enabled Whether to generate release notes for this library. Defaults to true.
 * @property name The variable name mapping for this library, defined on G3.
 * @property versionName The version name mapping for this library, defined on G3.
 * @property artifactName The name of the generation artifact. _Only_ required if your project's
 *   name is different than your generated artifact name. Defaults to the project name.
 * @see MakeReleaseNotesTask
 */
abstract class ReleaseNotesConfigurationExtension {
  abstract val enabled: Property<Boolean>
  abstract val name: Property<String>
  abstract val versionName: Property<String>
  abstract val artifactName: Property<String>
}
