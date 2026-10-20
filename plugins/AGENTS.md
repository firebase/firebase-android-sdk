# Gradle Plugins (`plugins/` Directory)

Agents that support directory-scoped context files load this file only when they read or edit
files under `plugins/`. It complements the repository-wide guidance in the root
[`AGENTS.md`](../AGENTS.md).

All custom build, release, CI, and documentation logic lives in `plugins/`, which is included as a
composite build (`includeBuild("./plugins")` in the root `settings.gradle.kts`).

- **Source layout**: `plugins/src/main/java/com/google/firebase/gradle/plugins/` (core plugins,
  tasks, and extensions) and `plugins/src/main/java/com/google/firebase/gradle/bomgenerator/` (BOM
  tasks).
- **Testing and formatting**: Run plugin unit and Gradle TestKit tests with `./gradlew plugins:check`
  and format plugin code with `./gradlew -p plugins spotlessApply`.

## Registered Plugins and Extensions

Plugins are registered in `plugins/build.gradle.kts`:

- **Library Plugins** (subclass `BaseFirebaseLibraryPlugin`):
  - `firebase-library` (`FirebaseAndroidLibraryPlugin`): Applied to Android SDK modules. Applies
    `com.android.library`, `LicenseResolverPlugin`, `DackkaPlugin`, and `GitSubmodulePlugin` (binds
    `updateGitSubmodules` to `preBuild`). Configures Java 8/Kotlin targets, `FirebaseTestServer`,
    Metalava API tasks (`apiInformation`, `generateApiTxtFile`, `docStubs`), `gmavenVersionCheck`,
    `metalavaSemver`, `makeReleaseNotes`, and Maven publishing.
  - `firebase-java-library` (`FirebaseJavaLibraryPlugin`): Applied to pure Java/Kotlin JVM library
    modules. Applies `java-library` and `DackkaPlugin`, and configures Metalava API tasks, version
    checks, `makeReleaseNotes`, and Maven publishing.
- **Root Orchestration Plugins** (applied in root `build.gradle.kts`):
  - `PublishingPlugin` (`PublishingPlugin`): Orchestrates release validation, artifact and doc
    packaging, release notes, BOM generation, and dynamically applies `PostReleasePlugin` to
    releasing modules.
  - `firebase-ci` (`ContinuousIntegrationPlugin`): Computes Git-affected subprojects and registers
    `*Dependents` and `*Changed` verification tasks (`checkChanged`, `checkCoverageChanged`,
    `deviceCheckChanged`, `writeChangedProjects`). Configured via the
    `firebaseContinuousIntegration` extension (`ignorePaths`).
  - `smoke-tests` (`SmokeTestsPlugin`): Registers `assembleAllForSmokeTests`, which runs
    `publishAllToBuildDir` and writes `build/m2repository/changed-artifacts.json`.
  - `test-report` (`TestReportPlugin`): Registers `generateTestReport`.
- **Utility Plugins**:
  - `firebase-vendor` (`VendorPlugin`): Shades and relocates dependencies declared on the `vendor`
    configuration into the module's namespace during `release` builds using JarJar
    (`<variant>VendorTransform`). Fails if `java.*` or `javax.*` classes are present in vendored
    dependencies, so those transitive dependencies must be excluded.
  - `LicenseResolverPlugin` (`LicenseResolverPlugin`): Creates the `thirdPartyLicenses` extension
    (`add(name, ...licenseUris)`) and registers `generateLicenses` to bundle third-party license
    files into the AAR.
  - `copy-google-services` (`CopyGoogleServicesPlugin`): Registers `copyRootGoogleServices` to copy
    the root `google-services.json` (or `plugins/resources/dummy-google-services.json` fallback, or
    `FIREBASE_GOOGLE_SERVICES_PATH`) into the module directory, rewriting all `package_name` fields
    to match the module's Android `namespace`. Automatically applies `com.google.gms.google-services`
    when instrumentation test tasks (`AndroidTest`, `connectedCheck`, `deviceCheck`) are invoked.

### `firebaseLibrary` Extension (`FirebaseLibraryExtension`)

Every `firebase-library` and `firebase-java-library` module exposes the `firebaseLibrary` DSL block:

- `groupId` / `artifactId`: Maven coordinates (default to project `group` and `name`).
- `libraryGroup`: Links sibling modules that must always release together and share identical
  versions (aligned automatically via `fixLibraryGroupVersions` in `LibraryGroups.kt`). Modules in
  the same `libraryGroup` are permitted to retain project-level dependencies (`project(":...")`) on
  one another across releases.
- `publishJavadoc` (default `true`) / `onlyPublishKotlindoc` (default `false`): Controls reference
  documentation generation via `DackkaPlugin`.
- `previewMode`: When set (for example, `"alpha"` or `"beta"`), release validation enforces that the
  module's `version` in `gradle.properties` contains that suffix.
- `androidLintCheckProjects`: Projects added to `lintChecks` (configurable via
  `-Pfirebase.checks.lintProjects`).
- `customizePom { ... }`: Customizes the generated Maven POM.
- `testLab` (`FirebaseTestLabExtension`): Configures Firebase Test Lab execution (`enabled`,
  `timeout`, `devices`).
- `releaseNotes` (`ReleaseNotesConfigurationExtension`): Configures `makeReleaseNotes` (`enabled`,
  `name`, `versionName`, `nestedHeader`, `artifactName`) for Devsite/Firesite variable substitution.

## Publishing Architecture and Task Execution

Publishing is split between per-module configuration in `BaseFirebaseLibraryPlugin` and root-level
orchestration in `PublishingPlugin`:

1. **Per-Module Publication (`BaseFirebaseLibraryPlugin`)**:
   - Applies `MavenPublishPlugin`, disables `GenerateModuleMetadata` on Android libraries, and
     registers a local repository named `BuildDir` at `<rootProject>/build/m2repository`.
   - Creates a `MavenPublication` named `mavenAar` from `firebaseLibrary.componentName` (`release`
     for Android or `java` for JVM libraries; switches to `debug` when `-PpublishDebugVariant=true`
     is set).
   - Transforms the generated POM XML (`applyPomTransformations`): inspects
     `releaseRuntimeClasspath` (or `runtimeClasspath`) to identify project and external Android
     dependencies and injects `<type>aar</type>` or `<type>jar</type>` into each `<dependency>`
     element. Notice that project-level dependencies on modules without `firebaseLibrary` will throw
     a `RuntimeException` during POM generation.
2. **Release Metadata Resolution (`PublishingPlugin.computeReleaseMetadata`)**:
   - Runs in `gradle.projectsEvaluated` and resolves the releasing module set from one of two
     sources (project properties take precedence):
     - **Project properties**: `-PprojectsToPublish="<artifactId1>,<artifactId2>"` (with optional
       `-PreleaseName="<name>"`). Automatically computes the transitive closure
       (`getDeepTransitiveReleases`) across both project-level dependencies and `libraryGroup`
       siblings.
     - **`release.json` (`ReleaseConfig`)**: Generated by `./gradlew generateReleaseConfig
       -PcurrentRelease=... -PpastRelease=...` (`ReleaseGenerator.kt`), which inspects Git commits
       touching `CHANGELOG.md` and outputs `release.json` and `release_report.md`. Unlike
       `-PprojectsToPublish`, `release.json` lists Gradle project paths and does **not**
       auto-expand; missing `libraryGroup` siblings cause an immediate `GradleException`.
3. **`firebasePublish` Execution Graph**:
   - **Validation tasks**:
     - `validateProjectsToPublish`: Ensures the releasing set is non-empty and every module's
       `version` satisfies its `previewMode`.
     - `validateLibraryGroupsToPublish`: Verifies all members of any releasing `libraryGroup` are
       included.
     - `checkHeadDependencies` (`CheckHeadDependencies.kt`): Verifies all project-level dependencies
       of releasing modules are also in the release (except `protolite-well-known-types`).
     - `validatePomForRelease` / `isPomDependencyValid` (`PomValidator.kt`): Diffs generated POM
       dependency versions against the latest released POM on GMaven to catch accidental version
       downgrades.
   - **Artifact tasks**:
     - `publishReleasingLibrariesToBuildDir` -> `buildMavenZip`: Runs
       `publishMavenAarPublicationToBuildDirRepository` for each releasing module and archives
       `<root>/build/m2repository` into `build/m2repository.zip`.
     - `generateKotlindocForRelease` -> `buildKotlindocZip`: Runs `kotlindoc` on each releasing
       module and archives the output into `build/kotlindoc.zip`.
     - `prepareReleaseNotesForDrop` -> `buildReleaseNotesZip`: Runs `makeReleaseNotes`
       (`MakeReleaseNotesTask.kt`, which parses the `# Unreleased` section of `CHANGELOG.md`) for
       each releasing module, stages files under
       `build/release-notes/android/client/<module>/_releases/<releaseName>.md`, and archives them
       into `build/release-notes.zip`.
   - **Post-release and BOM tasks**:
     - `postReleaseCleanup` (`PostReleasePlugin`, applied to releasing modules): Runs `versionBump`
       (`VersionBumpTask.kt`), `moveUnreleasedChanges` (`MoveUnreleasedChangesTask.kt`), and
       `updatePinnedDependencies` (`UpdatePinnedDependenciesTask.kt`, which rewrites `project(":...")`
       dependencies outside the module's `libraryGroup` back to pinned GMaven coordinates in the
       module's build file).
     - `generateBom`, `generateBomReleaseNotes`, `generateTutorialBundle`, and `buildBomBundleZip`:
       Generate the Firebase BOM and release collateral using `GMavenService`.

## Reference Documentation Generation (`DackkaPlugin`)

Reference documentation for Firesite (`firebase.google.com/docs/reference`) is generated by
`DackkaPlugin` (`DackkaPlugin.kt`, `DackkaGenerationTask.kt`, `FiresiteTransformTask.kt`). When
`firebaseLibrary.publishJavadoc` is `true`, invoking `./gradlew :<project>:kotlindoc` executes a
three-step pipeline:

1. **`generateDackkaDocumentation` (`GenerateDocumentationTask`)**:
   - Collects Java and Kotlin source directories from the Android `release` variant, combines
     `releaseCompileConfiguration` jars, `javadocClasspath` jars, and `bootClasspath`, and writes a
     JSON configuration file (`dackkaArgs*.json`) for `com.google.devsite.DevsitePlugin` (setting
     `javaDocsPath` to `"android"` unless `onlyPublishKotlindoc` is `true`, and `kotlinDocsPath` to
     `"kotlin"`).
   - Launches Dokka (`org.jetbrains.dokka.MainKt`) via a Gradle `WorkerExecutor` against the fat JAR
     at `<rootProject>/dackka-fat-1.2.0.jar`, outputting to a task temporary directory
     (`dackkaRawOutput`). Depends on `createFullJarRelease`.
2. **`firesiteTransform` (`FiresiteTransformTask`)**:
   - Transforms Devsite-formatted output in `dackkaRawOutput/docs/reference` into Firesite format
     (`dackkaTransformedFiles`):
     - Deletes `index.html`, `classes.html`, `packages.html`, and `package-list`.
     - Strips `Class Index` and `Package Index` sections, removes the `com.google.` prefix from
       package titles, and adds `status: deprecated` to `-ktx` packages in `_toc.yaml` files.
     - Strips module-specific prefixes from `book_path`, repairs malformed HTML hyperlinks inside
       `@see` blocks, and rewrites `.Companion.html` links to `.html` in generated HTML files.
3. **`copyDocsToCommonDirectory`**:
   - Copies transformed documentation into `<rootProject>/build/firebase-kotlindoc`.
   - Use `./gradlew :<project>:cleanDackkaDocumentation` to delete intermediate and root doc output
     directories when testing changes locally.

### Common Pitfalls and Failure Modes in Reference Documentation

The documentation tasks are brittle and prone to break in the following ways:

- **`firebase-java-library` incompatibility with `DackkaPlugin`**: `FirebaseJavaLibraryPlugin`
  applies `DackkaPlugin` and inherits `publishJavadoc = true` from `BaseFirebaseLibraryPlugin`.
  However, `registerGenerateDackkaDocumentationTask` unconditionally queries the Android
  `LibraryExtension` (`project.extensions.getByType<LibraryExtension>()`), depends on the Android
  task `createFullJarRelease`, and resolves `android-classes` artifacts. Any pure Java/Kotlin module
  using `firebase-java-library` **must** set `firebaseLibrary { publishJavadoc = false }`, or
  configuring `generateDackkaDocumentation` / running `kotlindoc` will fail.
- **Missing `<rootProject>/dackka-fat-1.2.0.jar`**: `generateDackkaDocumentation` requires
  `dackka-fat-1.2.0.jar` in the repository root. In CI (`.github/workflows/build-release-artifacts.yml`),
  this file is fetched from `gs://android-ci/mvn/com/google/devsite/dackka-fat/1.2.0/dackka-fat-1.2.0.jar`.
  If the JAR is absent locally, `kotlindoc` will fail.
- **Hardcoded `kotlindoc/package-lists` directory names**: `GenerateDocumentationTask` discovers all
  `kotlindoc/package-lists/**/package-list` files and looks up each file's parent directory name in
  a hardcoded map in `DackkaGenerationTask.kt` (`android`, `androidx`, `google`, `coroutines`,
  `firebase`, `kotlin`, `serialization`). Adding a new subdirectory under `kotlindoc/package-lists/`
  without registering its base URL in `createExternalLinks` throws
  `RuntimeException("Unexpected package-list found: ...")`.
- **Unresolved symbols from `compileOnly` or annotation processors**: Dackka resolves symbols from
  the Android `release` compile configuration and `javadocClasspath` (which `compileOnly` extends
  from). If a library uses compile-only annotations (such as `libs.autovalue.annotations`) needed
  during doc generation, declare them on `javadocClasspath`. Note that `javadocConfig.jars` resolves
  dependencies using the `artifactType = "android-classes"` attribute view.
- **Fragile regex transformations in `FiresiteTransformTask`**: HTML and `_toc.yaml` fixes in
  `FiresiteTransformTask` rely on exact regular expressions matching Dackka's output structure (for
  example, `@see <a href="...">` tags, `setvar book_path`, and `_toc.yaml` indentation). Malformed
  `@see` tags in KDoc/Javadoc or changes in Dackka's output format can silently leave broken links
  or corrupt `_toc.yaml`.
- **Metalava `docStubs` runs during `check`**: Both `firebase-library` and `firebase-java-library`
  register a Metalava `docStubs` (`GenerateStubsTask`) task and attach it to `check`. Invalid
  Javadoc/KDoc syntax or missing classpath references in doc comments can cause `./gradlew
  :<project>:check` to fail in `docStubs`.
