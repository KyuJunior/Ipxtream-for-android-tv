package com.ipxtream.tv.data.api

import okhttp3.Interceptor

/** GitHub build: no demo sign-in (the Play build has one for Google's reviewers, see src/play). */
object DemoCatalog {
    const val SERVER = ""
    const val USERNAME = ""
    const val PASSWORD = ""

    @Suppress("UNUSED_PARAMETER")
    fun isDemo(server: String): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun streamUrl(server: String, streamId: Int): String? = null

    val interceptor: Interceptor? = null
}
