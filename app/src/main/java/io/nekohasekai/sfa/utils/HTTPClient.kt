package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class HTTPClient : Closeable {

    data class Response(
        val body: String,
        val headers: Map<String, String>
    )

    fun request(url: String): Response {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = DEFAULT_CONNECT_TIMEOUT
            connection.readTimeout = DEFAULT_READ_TIMEOUT
            connection.setRequestProperty("User-Agent", userAgent)
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode: $body")
            }
            Response(
                body = body,
                headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapKeys { it.key!!.lowercase(Locale.ROOT) }
                    .mapValues { it.value?.joinToString(",") ?: "" }
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun getString(url: String): String = request(url).body

    override fun close() {
        // nothing to close
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT = 15000
        private const val DEFAULT_READ_TIMEOUT = 20000

        val userAgent by lazy {
            var userAgent = "SFA/"
            userAgent += BuildConfig.VERSION_NAME
            userAgent += " ("
            userAgent += BuildConfig.VERSION_CODE
            userAgent += "; sing-box "
            userAgent += Libbox.version()
            userAgent += "; language "
            userAgent += Locale.getDefault().toLanguageTag().replace("-", "_")
            userAgent += ")"
            userAgent
        }
    }
}
