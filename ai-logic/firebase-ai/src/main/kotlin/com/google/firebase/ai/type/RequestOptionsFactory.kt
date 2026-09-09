package com.google.firebase.ai.type

/**
 * Factory for creating [RequestOptions] with custom configurations.
 * This is primarily intended for use by wrapper libraries and internal tools.
 */
public object RequestOptionsFactory {
  public fun createWithCustomHeader(
    baseOptions: RequestOptions,
    customApiClientHeader: String
  ): RequestOptions {
    return RequestOptions(
      timeout = baseOptions.timeout,
      endpoint = baseOptions.endpoint,
      apiVersion = baseOptions.apiVersion,
      autoFunctionCallingTurnLimit = baseOptions.autoFunctionCallingTurnLimit,
      customApiClientHeader = customApiClientHeader
    )
  }
}
