package com.google.firebase.dataconnect.testutil

import android.content.res.AssetManager
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.dataconnect.FirebaseDataConnect
import firemat.emulator.server.api.EmulatorServiceGrpcKt.EmulatorServiceCoroutineStub
import firemat.emulator.server.api.file
import firemat.emulator.server.api.setupSchemaRequest
import firemat.emulator.server.api.source
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.io.InputStreamReader
import java.lang.Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

suspend fun FirebaseDataConnect.installEmulatorSchema(
  schema: String,
  operationSets: Map<String, String>
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
      serviceId = serviceConfig.serviceId,
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

  val schema =
    withContext(Dispatchers.IO) {
      assets.open("$assetDir/$schemaFileName").use { inputStream ->
        InputStreamReader(inputStream, Charsets.UTF_8).readText()
      }
    }

  val loadedAssets =
    loadAssets(assets, assetDir) { it.endsWith(".gql") && it != schemaFileName }
      .flowOn(Dispatchers.IO)
      .toList()

  val operationSets = mutableMapOf<String, String>()
  loadedAssets.forEach {
    val operationSet = it.fileName.run { substring(0, length - 4) }
    operationSets[operationSet] = it.contents
  }

  installEmulatorSchema(schema = schema, operationSets = operationSets)
}

private fun loadAssets(assets: AssetManager, dirPath: String, filter: (String) -> Boolean) = flow {
  val fileNames =
    assets.list(dirPath) ?: throw NoSuchAssetError("AssetManager.list($dirPath) returned null")
  fileNames.filter(filter).forEach { fileName ->
    val contents =
      assets.open("$dirPath/$fileName").use { inputStream ->
        InputStreamReader(inputStream, Charsets.UTF_8).readText()
      }
    emit(LoadedAsset(fileName = fileName, contents = contents))
  }
}

private data class LoadedAsset(val fileName: String, val contents: String)

private class NoSuchAssetError(message: String) : Exception(message)

private suspend fun setupSchema(
  grpcStub: EmulatorServiceCoroutineStub,
  serviceId: String,
  schema: String,
  operationSets: Map<String, String>
) {
  grpcStub.setupSchema(
    setupSchemaRequest {
      this.serviceId = serviceId
      this.schema = source {
        this.files.add(
          file {
            this.path = "schema/schema.gql"
            this.content = schema
          }
        )
      }
      operationSets.forEach { (operationSetName, queriesAndMutations) ->
        this.operationSets.put(
          operationSetName,
          source {
            this.files.add(
              file {
                this.path = "schema/queriesAndMutations.gql"
                this.content = queriesAndMutations
              }
            )
          }
        )
      }
    }
  )
}
