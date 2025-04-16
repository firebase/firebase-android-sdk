rootProject.name = "dataconnect-ci"

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }

  // Reuse libs.version.toml from the main Gradle project.
  versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
