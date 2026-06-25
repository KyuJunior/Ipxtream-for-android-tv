package com.ipxtream.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Top-level response from the Xtream Codes player_api.php login call.
 */
data class XtreamAuthResponse(
    @SerializedName("user_info")   val userInfo:   UserInfo,
    @SerializedName("server_info") val serverInfo: ServerInfo
)

/**
 * Per-account metadata returned by the Xtream Codes API.
 * [auth] == 1 indicates a successful authentication.
 */
data class UserInfo(
    @SerializedName("username")              val username:             String,
    @SerializedName("password")              val password:             String,
    @SerializedName("message")               val message:              String?,
    /** 1 = authenticated, 0 = rejected */
    @SerializedName("auth")                  val auth:                 Int,
    @SerializedName("status")                val status:               String,
    /** Unix timestamp string, or null for trial/expired accounts */
    @SerializedName("exp_date")              val expDate:              String?,
    @SerializedName("is_trial")              val isTrial:              String?,
    @SerializedName("active_cons")           val activeCons:           String?,
    @SerializedName("created_at")            val createdAt:            String?,
    @SerializedName("max_connections")       val maxConnections:       String?,
    @SerializedName("allowed_output_formats")val allowedOutputFormats: List<String>
)

/**
 * Server-side configuration returned alongside user info.
 */
data class ServerInfo(
    @SerializedName("url")             val url:            String,
    @SerializedName("port")            val port:           String,
    @SerializedName("https_port")      val httpsPort:      String?,
    @SerializedName("server_protocol") val serverProtocol: String,
    @SerializedName("rtmp_port")       val rtmpPort:       String?,
    @SerializedName("timezone")        val timezone:       String,
    @SerializedName("timestamp_now")   val timestampNow:   Long,
    @SerializedName("time_now")        val timeNow:        String
)
