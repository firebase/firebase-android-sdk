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
    val gradlewExecutable: File = gradlewExecutable.get().asFile

    runCommandIgnoringExitCode("uname", "-a")
    runCommandIgnoringExitCode("which", "java")
    runCommandIgnoringExitCode("java", "-version")
    runCommandIgnoringExitCode("which", "javac")
    runCommandIgnoringExitCode("javac", "-version")
    runCommandIgnoringExitCode("which", "node")
    runCommandIgnoringExitCode("node", "--version")
    runCommandIgnoringExitCode(firebaseExecutable.path, "--version")
    runCommandIgnoringExitCode(gradlewExecutable.path, "--version")
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

abstract class BaseGradleTask(@get:Internal val gradleTasks: List<String>) : DefaultTask() {

  @get:Internal abstract val gradleInfoLogsEnabled: Property<Boolean>

  @get:Input abstract val gradleWorkingDir: Property<File>

  @get:InputFile abstract val gradlewExecutable: RegularFileProperty

  @get:Inject abstract val execOperations: ExecOperations

  fun initializeProperties(
    gradleWorkingDir: Provider<Directory>,
    gradlewExecutable: Provider<RegularFile>,
    gradleInfoLogsEnabled: Provider<Boolean>,
  ) {
    this.gradleWorkingDir.set(gradleWorkingDir.map { it.asFile })
    this.gradlewExecutable.set(gradlewExecutable)
    this.gradleInfoLogsEnabled.set(gradleInfoLogsEnabled)
  }

  @TaskAction
  fun execute() {
    val gradleWorkingDir: File = gradleWorkingDir.get()
    val gradlewExecutable: File = gradlewExecutable.get().asFile
    val gradleInfoLogsEnabled: Boolean = gradleInfoLogsEnabled.get()

    val gradleArgs = buildList {
      add(gradlewExecutable.path)
      if (gradleInfoLogsEnabled) {
        add("--info")
      }
      add("--profile")
      add("--configure-on-demand")
      addAll(gradleTasks)
    }

    execOperations.exec {
      workingDir = gradleWorkingDir
      commandLine(gradleArgs)
      logger.lifecycle("Running command in directory $workingDir: ${commandLine.joinToString(" ")}")
    }
  }
}

abstract class BuildDataConnectIntegrationTests : BaseGradleTask(assembleTasks) {
  companion object {
    val assembleTasks =
      listOf(
        ":firebase-dataconnect:assembleDebugAndroidTest",
        ":firebase-dataconnect:connectors:assembleDebugAndroidTest",
      )
  }
}

abstract class RunDataConnectIntegrationTests : BaseGradleTask(connectedCheckTasks) {
  companion object {
    val connectedCheckTasks =
      listOf(
        ":firebase-dataconnect:connectedCheck",
        ":firebase-dataconnect:connectors:connectedCheck",
      )
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

val gradleWorkingDirProvider = providers.provider { layout.projectDirectory.dir("../..") }

val gradlewExecutableProvider = providers.provider { layout.projectDirectory.file("../../gradlew") }

val gradlewInfoLogsEnabledProvider =
  providers.requiredGradleProperty("debugLoggingEnabled").map {
    when (it) {
      "1" -> true
      "0" -> false
      else ->
        throw IllegalArgumentException(
          "invalid value for debugLoggingEnabled property: $it (must be either 0 or 1)"
        )
    }
  }

tasks.register<PrintToolVersions>("printToolVersions") {
  group = ciTaskGroup
  description = "Print versions of notable command-line tools"
  initializeProperties(
    firebaseExecutable = installFirebaseToolsTask.flatMap { it.firebaseExecutable },
    gradlewExecutable = gradlewExecutableProvider,
  )
}

tasks.register<BuildDataConnectIntegrationTests>("buildIntegrationTests") {
  group = ciTaskGroup
  description = "Build the Data Connect integration tests"
  initializeProperties(
    gradleWorkingDir = gradleWorkingDirProvider,
    gradlewExecutable = gradlewExecutableProvider,
    gradleInfoLogsEnabled = gradlewInfoLogsEnabledProvider,
  )
}

tasks.register<RunDataConnectIntegrationTests>("runIntegrationTests") {
  group = ciTaskGroup
  description = "Run the Data Connect integration tests"
  initializeProperties(
    gradleWorkingDir = gradleWorkingDirProvider,
    gradlewExecutable = gradlewExecutableProvider,
    gradleInfoLogsEnabled = gradlewInfoLogsEnabledProvider,
  )
}
