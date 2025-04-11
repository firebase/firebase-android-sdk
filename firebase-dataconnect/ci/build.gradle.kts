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

  @get:OutputFile abstract val firebaseExecutable: RegularFileProperty

  @get:Inject abstract val execOperations: ExecOperations

  fun initializeProperties(version: Provider<String>, destDir: Provider<Directory>) {
    this.version.set(version)
    this.destDir.set(destDir)
    this.firebaseExecutable.set(destDir.map { it.dir("node_modules").dir(".bin").file("firebase") })
  }

  @TaskAction
  fun execute() {
    val destDir: File = destDir.get().asFile
    val version: String = version.get()
    val firebaseExecutable: File = firebaseExecutable.get().asFile

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

    execOperations.exec {
      setCommandLine(firebaseExecutable.path, "--version")
      logger.lifecycle(
        "Running command to verify successful installation: ${commandLine.joinToString(" ")}"
      )
    }
  }
}

val installFirebaseToolsTask =
  tasks.register<InstallFirebaseToolsTask>("installFirebaseTools") {
    group = "Data Connect CI"
    description = "Install the firebase-tools npm package"
    initializeProperties(
      version = providers.requiredGradleProperty("firebaseToolsVersion"),
      destDir = layout.buildDirectory.dir("firebase-tools"),
    )
  }

fun ProviderFactory.requiredGradleProperty(propertyName: String) =
  gradleProperty(propertyName)
    .orElse(
      providers.provider<Nothing> {
        throw RequiredPropertyMissing(
          "Required project property \"$propertyName\" was not set; " +
            "consider setting it " +
            "by specifying -P$propertyName=<value> on the Gradle command line " +
            "or by setting the environment variable ORG_GRADLE_PROJECT_$propertyName"
        )
      }
    )

class RequiredPropertyMissing(message: String) : Exception(message)
