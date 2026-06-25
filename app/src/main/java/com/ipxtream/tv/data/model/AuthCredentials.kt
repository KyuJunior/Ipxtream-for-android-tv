package com.ipxtream.tv.data.model

/**
 * In-memory representation of the user's login credentials.
 * Never stored as plain-text; use [com.ipxtream.tv.data.local.CredentialStore]
 * for encrypted persistence.
 */
data class AuthCredentials(
    val server: String,
    val username: String,
    val password: String
)
