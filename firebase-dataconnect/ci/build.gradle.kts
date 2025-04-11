import java.io.File
import org.gradle.kotlin.dsl.*

plugins { alias(libs.plugins.spotless) }

spotless {
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt("0.41").googleStyle()
  }
}

abstract class InstallFirebaseToolsTask : DefaultTask() {

  @get:Input abstract val version: Property<String>

  @get:OutputDirectory abstract val destDir: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun execute() {
    val destDir: File = destDir.get().asFile
    val version: String = version.get()

    val packageJsonFile = File(destDir, "package.json")
    logger.lifecycle("Creating $packageJsonFile")
    packageJsonFile.writeText("{}")

    execOperations.exec {
      workingDir = destDir
      setCommandLine(
        "npm",
        "install",
        "--fund=false",
        "--audit=false",
        "--save",
        "--save-exact",
        "firebase-tools@$version",
      )
      logger.lifecycle("Running command in directory $workingDir: ${commandLine.joinToString(" ")}")
    }
  }
}

val installFirebaseToolsTask = tasks.register<InstallFirebaseToolsTask>("installFirebaseTools") {
  group = "Data Connect CI"
  description = "Install the firebase-tools npm package"
  destDir.set(layout.buildDirectory.dir("firebase-tools"))
  version.set(providers.requiredGradleProperty("firebaseToolsVersion"))
}

fun ProviderFactory.requiredGradleProperty(propertyName: String) =
  gradleProperty(propertyName)
    .orElse(
      providers.provider<Nothing> {
        throw RequiredPropertyMissing(
          "zzyzx=${project.property(propertyName)} Project property \"$propertyName\" was not set, " +
            "but is required. " +
            "Consider setting this project property by specifying " +
            "-P$propertyName=<value> on the Gradle command line, " +
            "or by setting the ORG_GRADLE_PROJECT_installFirebaseTools environment variable."
        )
      }
    )

class RequiredPropertyMissing(message: String) : Exception(message)
