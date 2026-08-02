// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/NetworkEngine.kt]

package com.easyweb.app.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

// 网络请求结果
data class FetchResult(
    val success: Boolean,
    val content: String,
    val url: String,
    val error: String? = null
)

// 网络引擎 负责HTTP请求
class NetworkEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 同步获取URL内容
    fun fetch(url: String): FetchResult {
        var resolvedUrl = url
        // 自动补全协议
        if (!resolvedUrl.startsWith("http://") && !resolvedUrl.startsWith("https://")) {
            resolvedUrl = "https://$resolvedUrl"
        }

        return try {
            val request = Request.Builder()
                .url(resolvedUrl)
                .header("User-Agent", "EasyWeb/1.0 (Android; Lightweight Browser Engine)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                FetchResult(
                    success = true,
                    content = body,
                    url = response.request.url.toString()
                )
            } else {
                FetchResult(
                    success = false,
                    content = "",
                    url = resolvedUrl,
                    error = "HTTP ${response.code}: ${response.message}"
                )
            }
        } catch (e: IOException) {
            FetchResult(
                success = false,
                content = "",
                url = resolvedUrl,
                error = "请求失败: ${e.localizedMessage ?: e.message}"
            )
        } catch (e: Exception) {
            FetchResult(
                success = false,
                content = "",
                url = resolvedUrl,
                error = "未知错误: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    // 解析相对URL为绝对URL
    fun resolveUrl(base: String, relative: String): String {
        return try {
            URL(URL(base), relative).toString()
        } catch (e: Exception) {
            relative
        }
    }
}