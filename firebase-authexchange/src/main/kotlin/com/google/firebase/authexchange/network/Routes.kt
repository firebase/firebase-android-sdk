package com.google.firebase.authexchange.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines what an HTTP request may look like when interacting with the backend API for
 * [FirebaseAuthExchange][com.google.firebase.authexchange.FirebaseAuthExchange].
 */
@Serializable internal sealed interface Request

/** Defines what an HTTP response may look like when submitting a [Request]. */
@Serializable internal sealed interface Response

/**
 * OIDC Credential.
 *
 * Useful for apps with external login already set-up, otherwise known as a headless OIDC flow.
 *
 * @property idToken JWT encoded OIDC token returned from a third party provider
 */
@Serializable internal data class ImplicitCredentialsP(val idToken: String)

/**
 * Auth Token that can be used to access certain Firebase Services.
 *
 * This is merely the proto type for the network layer, and is completely separate from the user
 * facing [AuthExchangeToken][com.google.firebase.authexchange.AuthExchangeToken].
 *
 * @property accessToken signed JWT containing claims that identify a user
 * @property timeToLive the duration from the time this token is minted until its expiration
 */
@Serializable
internal data class AuthExchangeTokenP(
  val accessToken: String,
  @SerialName("ttl") val timeToLive: String
)

/**
 * Request header for the `/ExchangeInstallationAuthToken` endpoint.
 *
 * @see ExchangeTokenResponseP
 *
 * @property token relative resource name of the audience project and location
 * @property installationAuthToken the installation token issued to the app
 */
@Serializable
internal data class ExchangeInstallationAuthTokenRequestP(
  val token: String,
  val installationAuthToken: String
) : Request

/**
 * Request header for the `/ExchangeCustomToken` endpoint.
 *
 * @see ExchangeTokenResponseP
 *
 * @property token relative resource name of the audience project and location
 * @property customToken a custom JWT token signed with the developer's credentials
 */
@Serializable
internal data class ExchangeCustomTokenRequestP(val token: String, val customToken: String) :
  Request

/**
 * Request header for the `/ExchangeOidcToken` endpoint.
 *
 * @see ExchangeOidcTokenResponseP
 *
 * @property token relative resource name of the audience project and location
 * @property providerId the display name or id of the OIDC provider
 * @property implicitCredentials JWT token from the OIDC provider, provided by the developer
 */
@Serializable
internal data class ExchangeOidcTokenRequestP(
  val token: String,
  val providerId: String,
  val implicitCredentials: ImplicitCredentialsP
) : Request

/**
 * Response header for endpoints that just expect an [AuthExchangeTokenP].
 *
 * @see ExchangeCustomTokenRequestP
 * @see ExchangeInstallationAuthTokenRequestP
 *
 * @property token auth token returned by the backend
 */
@Serializable internal data class ExchangeTokenResponseP(val token: AuthExchangeTokenP) : Response

/**
 * Response header for the `/ExchangeOidcToken` endpoint.
 *
 * @see ExchangeOidcTokenRequestP
 *
 * @property authExchangeToken auth token returned by the backend
 * @property oidcIdToken OAuth id token received from the OIDC provider
 * @property oidcRefreshToken optional OAuth refresh token received from the OIDC provider
 */
@Serializable
internal data class ExchangeOidcTokenResponseP(
  val authExchangeToken: AuthExchangeTokenP,
  val oidcIdToken: String,
  val oidcRefreshToken: String? = null
) : Response
