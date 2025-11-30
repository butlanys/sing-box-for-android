package io.nekohasekai.sfa.ui.dashboard

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.utils.HTTPClient
import java.net.HttpURLConnection
import java.net.URL

enum class SiteRegion(@StringRes val labelRes: Int) {
    Domestic(R.string.major_site_latency_region_domestic),
    Global(R.string.major_site_latency_region_global)
}

data class MajorSite(
    val id: String,
    @StringRes val nameRes: Int,
    val url: String,
    @DrawableRes val iconRes: Int,
    val region: SiteRegion
)

data class MajorSiteLatencyState(
    val site: MajorSite,
    val status: LatencyStatus = LatencyStatus.Idle,
    val latencyMs: Int? = null
)

enum class LatencyStatus {
    Idle,
    Testing,
    Success,
    Failure
}

object MajorSiteCatalog {
    val sites = listOf(
        MajorSite(
            id = "bytedance",
            nameRes = R.string.major_site_latency_bytedance,
            url = "https://www.bytedance.com",
            iconRes = R.drawable.ic_latency_bytedance,
            region = SiteRegion.Domestic
        ),
        MajorSite(
            id = "wechat",
            nameRes = R.string.major_site_latency_wechat,
            url = "https://wx.qq.com",
            iconRes = R.drawable.ic_latency_wechat,
            region = SiteRegion.Domestic
        ),
        MajorSite(
            id = "bilibili",
            nameRes = R.string.major_site_latency_bilibili,
            url = "https://www.bilibili.com",
            iconRes = R.drawable.ic_latency_bilibili,
            region = SiteRegion.Domestic
        ),
        MajorSite(
            id = "taobao",
            nameRes = R.string.major_site_latency_taobao,
            url = "https://www.taobao.com",
            iconRes = R.drawable.ic_latency_taobao,
            region = SiteRegion.Domestic
        ),
        MajorSite(
            id = "github",
            nameRes = R.string.major_site_latency_github,
            url = "https://www.github.com",
            iconRes = R.drawable.ic_latency_github,
            region = SiteRegion.Global
        ),
        MajorSite(
            id = "cloudflare",
            nameRes = R.string.major_site_latency_cloudflare,
            url = "https://www.cloudflare.com",
            iconRes = R.drawable.ic_latency_cloudflare,
            region = SiteRegion.Global
        ),
        MajorSite(
            id = "jsdelivr",
            nameRes = R.string.major_site_latency_jsdelivr,
            url = "https://www.jsdelivr.com",
            iconRes = R.drawable.ic_latency_jsdelivr,
            region = SiteRegion.Global
        ),
        MajorSite(
            id = "youtube",
            nameRes = R.string.major_site_latency_youtube,
            url = "https://www.youtube.com",
            iconRes = R.drawable.ic_latency_youtube,
            region = SiteRegion.Global
        ),
    )
}

data class LatencyResult(val success: Boolean, val latencyMs: Int?)

object MajorSiteLatencyTester {

    fun measure(site: MajorSite, timeoutMs: Int = 5000): LatencyResult {
        val start = SystemClock.elapsedRealtime()
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(site.url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", HTTPClient.userAgent)
            connection.inputStream.use { /* discard */ }
            LatencyResult(
                success = true,
                latencyMs = (SystemClock.elapsedRealtime() - start).toInt()
            )
        } catch (_: Exception) {
            LatencyResult(success = false, latencyMs = null)
        } finally {
            connection?.disconnect()
        }
    }
}
