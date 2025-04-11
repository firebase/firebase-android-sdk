import java.io.File
import java.io.IOException
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

abstract class PrintToolVersions : DefaultTask() {

  @get:InputFile abstract val gradlewExecutable: RegularFileProperty

  @get:InputFile abstract val firebaseExecutable: RegularFileProperty

  @get:Inject abstract val execOperations: ExecOperations

  fun initializeProperties(
    firebaseExecutable: Provider<RegularFile>,
    gradlewExecutable: Provider<RegularFile>
  ) {
    this.firebaseExecutable.set(firebaseExecutable)
    this.gradlewExecutable.set(gradlewExecutable)
  }

  @TaskAction
  fun execute() {
    val firebaseExecutable: File = firebaseExecutable.get().asFile
    val gradlewFile: File = gradlewExecutable.get().asFile

    runCommandIgnoringExitCode("uname", "-a")
    runCommandIgnoringExitCode("which", "java")
    runCommandIgnoringExitCode("java", "-version")
    runCommandIgnoringExitCode("which", "javac")
    runCommandIgnoringExitCode("javac", "-version")
    runCommandIgnoringExitCode("which", "node")
    runCommandIgnoringExitCode("node", "--version")
    runCommandIgnoringExitCode(firebaseExecutable.path, "--version")
    runCommandIgnoringExitCode(gradlewFile.path, "--version")
  }

  private fun runCommandIgnoringExitCode(vararg args: String) {
    val argsStr = args.joinToString(" ")
    logger.lifecycle("Running command: $argsStr")

    val execResult =
      try {
        execOperations.exec {
          commandLine(*args)
          isIgnoreExitValue = true
        }
      } catch (e: Exception) {
        var ioException: Throwable? = e
        while (ioException !== null && ioException !is IOException) {
          ioException = ioException.cause
        }
        if (ioException !== null) {
          logger.warn("WARNING: unable to start process: $argsStr (${e.message})")
          return
        } else {
          throw e
        }
      }

    if (execResult.exitValue != 0) {
      logger.warn(
        "WARNING: command completed with non-zero exit code " + "${execResult.exitValue}: $argsStr"
      )
    }
  }
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

val ciTaskGroup = "Data Connect CI"

val installFirebaseToolsTask =
  tasks.register<InstallFirebaseToolsTask>("installFirebaseTools") {
    group = ciTaskGroup
    description = "Install the firebase-tools npm package"
    initializeProperties(
      version = providers.requiredGradleProperty("firebaseToolsVersion"),
      destDir = layout.buildDirectory.dir("firebase-tools"),
    )
  }

val printToolVersionsTask =
  tasks.register<PrintToolVersions>("printToolVersions") {
    group = ciTaskGroup
    description = "Print versions of notable command-line tools"
    val gradlewExecutable = layout.projectDirectory.file("../../gradlew")
    initializeProperties(
      firebaseExecutable = installFirebaseToolsTask.flatMap { it.firebaseExecutable },
      gradlewExecutable = providers.provider { gradlewExecutable },
    )
  }
