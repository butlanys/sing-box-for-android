package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.TypedProfile
import java.util.Locale

object SubscriptionTrafficParser {

    fun parse(header: String?): TypedProfile.SubscriptionTraffic? {
        if (header.isNullOrBlank()) return null
        val pairs = header.split(';')
        var hasValue = false
        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        for (pair in pairs) {
            val parts = pair.trim().split('=')
            if (parts.size != 2) continue
            val key = parts[0].trim().lowercase(Locale.ROOT)
            val value = parts[1].trim().toLongOrNull() ?: continue
            when (key) {
                "upload" -> upload = value
                "download" -> download = value
                "total" -> total = value
                "expire" -> expire = value
            }
            hasValue = true
        }
        if (!hasValue) return null
        return TypedProfile.SubscriptionTraffic(
            upload = upload,
            download = download,
            total = total,
            expireAt = expire * 1000L
        )
    }
}
