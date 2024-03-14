package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.*
import java.util.Objects

internal sealed class QueryExecutorResult(val queryExecutor: QueryExecutor, val requestId: String) {
  override fun equals(other: Any?) =
    other is QueryExecutorResult &&
      other.queryExecutor === queryExecutor &&
      other.requestId == requestId
  override fun hashCode() = Objects.hash("Result", queryExecutor, requestId)
  override fun toString() =
    "QueryExecutorResult(" + "queryExecutor=$queryExecutor, " + "requestId=$requestId" + ")"

  class Success(
    queryExecutor: QueryExecutor,
    requestId: String,
    val operationResult: DataConnectGrpcClient.OperationResult
  ) : QueryExecutorResult(queryExecutor, requestId) {
    override fun equals(other: Any?) =
      other is Success && super.equals(other) && other.operationResult == operationResult
    override fun hashCode() = Objects.hash("Success", super.hashCode(), operationResult)
    override fun toString() =
      "QueryExecutorResult.Success(" +
        "queryExecutor=$queryExecutor, " +
        "requestId=$requestId" +
        "operationResult=$operationResult" +
        ")"
  }

  class Failure(
    queryExecutor: QueryExecutor,
    requestId: String,
    val exception: DataConnectException
  ) : QueryExecutorResult(queryExecutor, requestId) {
    override fun equals(other: Any?) =
      other is Failure && super.equals(other) && other.exception == exception
    override fun hashCode() = Objects.hash("Failure", super.hashCode(), exception)
    override fun toString() =
      "QueryExecutorResult.Failure(" +
        "queryExecutor=$queryExecutor, " +
        "requestId=$requestId" +
        "exception=$exception" +
        ")"
  }
}

internal fun SequencedReference<QueryExecutorResult>.successOrNull():
  SequencedReference<QueryExecutorResult.Success>? =
  this.asTypeOrNull<QueryExecutorResult, QueryExecutorResult.Success>()

internal fun SequencedReference<QueryExecutorResult>.successOrThrow():
  SequencedReference<QueryExecutorResult.Success> =
  this.asTypeOrThrow<QueryExecutorResult, QueryExecutorResult.Success>()

internal fun SequencedReference<QueryExecutorResult>.failureOrNull():
  SequencedReference<QueryExecutorResult.Failure>? =
  this.asTypeOrNull<QueryExecutorResult, QueryExecutorResult.Failure>()

internal fun SequencedReference<QueryExecutorResult>.failureOrThrow():
  SequencedReference<QueryExecutorResult.Failure> =
  this.asTypeOrThrow<QueryExecutorResult, QueryExecutorResult.Failure>()
