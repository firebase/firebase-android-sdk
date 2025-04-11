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

  @get:Input abstract val firebaseToolsVersion: Property<String>

  @get:OutputDirectory abstract val installDir: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun execute() {
    val installDir: File = installDir.get().asFile
    val firebaseToolsVersion: String = firebaseToolsVersion.get()

    logger.lifecycle("Creating directory: $installDir")
    installDir.mkdirs()

    val packageJsonFile = File(installDir, "package.json")
    logger.lifecycle("Creating $packageJsonFile")
    packageJsonFile.writeText("{}")

    execOperations.exec {
      workingDir = installDir
      setCommandLine(
        "npm",
        "install",
        "--fund=false",
        "--audit=false",
        "--save",
        "--save-exact",
        "firebase-tools@$firebaseToolsVersion",
      )
      logger.lifecycle("Running command in directory $workingDir: ${commandLine.joinToString(" ")}")
    }
  }
}

tasks.register<InstallFirebaseToolsTask>("installFirebaseTools") {
  val projectDirectory = layout.projectDirectory
  installDir.set(providers.gradleProperty("installDir").map { projectDirectory.dir(it) })
  firebaseToolsVersion.set(providers.gradleProperty("firebaseToolsVersion"))
}

class RequiredPropertyMissing(message: String) : Exception(message)
