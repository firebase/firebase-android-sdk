package com.google.firebase.dataconnect.testutil

import android.content.res.AssetManager
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.dataconnect.FirebaseDataConnect
import google.firebase.dataconnect.emulator.EmulatorServiceGrpcKt.EmulatorServiceCoroutineStub
import google.firebase.dataconnect.emulator.file
import google.firebase.dataconnect.emulator.setupSchemaRequest
import google.firebase.dataconnect.emulator.source
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.io.InputStreamReader
import java.lang.Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

data class EmulatorSchemaInfo(val filePath: String, val contents: String)

suspend fun FirebaseDataConnect.installEmulatorSchema(
  schema: EmulatorSchemaInfo,
  operationSets: Map<String, EmulatorSchemaInfo>
) {
  val (hostName, port) = settings.run { Pair(hostName, port) }

  val grpcChannel =
    ManagedChannelBuilder.forAddress(hostName, port).let {
      it.usePlaintext()
      it.executor(Dispatchers.IO.asExecutor())
      AndroidChannelBuilder.usingBuilder(it).context(Firebase.app.applicationContext).build()
    }

  try {
    setupSchema(
      EmulatorServiceCoroutineStub(grpcChannel),
      serviceId = config.service,
      schema = schema,
      operationSets = operationSets
    )
  } finally {
    grpcChannel.shutdown()
  }
}

suspend fun FirebaseDataConnect.installEmulatorSchema(assetDir: String) {
  val assets = app.applicationContext.assets

  val schemaFileName = "schema.gql"
  val schemaPath = "$assetDir/$schemaFileName"
  val schemaContents =
    withContext(Dispatchers.IO) {
      assets.open(schemaPath).use { inputStream ->
        InputStreamReader(inputStream, Charsets.UTF_8).readText()
      }
    }
  val schema = EmulatorSchemaInfo(filePath = schemaPath, contents = schemaContents)

  val loadedAssets =
    loadAssets(assets, assetDir) { it.endsWith(".gql") && it != schemaFileName }.toList()

  val operationSets = buildMap {
    loadedAssets.forEach {
      val operationSetName =
        it.filePath.run {
          val startIndex =
            it.filePath.lastIndexOf('/').let { lastSlashIndex ->
              if (lastSlashIndex < 0) 0 else (lastSlashIndex + 1)
            }
          substring(startIndex, length - 4)
        }
      put(operationSetName, EmulatorSchemaInfo(filePath = it.filePath, contents = it.contents))
    }
  }

  installEmulatorSchema(schema = schema, operationSets = operationSets)
}

private fun loadAssets(assets: AssetManager, dirPath: String, filter: (String) -> Boolean) = flow {
  val fileNames =
    withContext(Dispatchers.IO) {
      assets.list(dirPath) ?: throw NoSuchAssetError("AssetManager.list($dirPath) returned null")
    }

  fileNames.filter(filter).forEach { fileName ->
    val assetPath = "$dirPath/$fileName"
    val contents =
      withContext(Dispatchers.IO) {
        assets.open(assetPath).use { inputStream ->
          InputStreamReader(inputStream, Charsets.UTF_8).readText()
        }
      }
    emit(LoadedAsset(filePath = assetPath, contents = contents))
  }
}

private data class LoadedAsset(val filePath: String, val contents: String)

private class NoSuchAssetError(message: String) : Exception(message)

private suspend fun setupSchema(
  grpcStub: EmulatorServiceCoroutineStub,
  serviceId: String,
  schema: EmulatorSchemaInfo,
  operationSets: Map<String, EmulatorSchemaInfo>
) {
  grpcStub.setupSchema(
    setupSchemaRequest {
      this.serviceId = serviceId
      this.schema = source {
        this.files.add(
          file {
            this.path = schema.filePath
            this.content = schema.contents
          }
        )
      }
      operationSets.forEach { (operationSetName, emulatorSchemaInfo) ->
        this.operationSets.put(
          operationSetName,
          source {
            this.files.add(
              file {
                this.path = emulatorSchemaInfo.filePath
                this.content = emulatorSchemaInfo.contents
              }
            )
          }
        )
      }
    }
  )
}
