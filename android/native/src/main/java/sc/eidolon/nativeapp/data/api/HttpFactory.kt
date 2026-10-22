package sc.eidolon.nativeapp.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import sc.eidolon.nativeapp.data.local.AccessCredentials
import sc.eidolon.nativeapp.data.local.BasicAuthCredentials
import java.io.File
import java.util.concurrent.TimeUnit

class HttpFactory(
    context: Context,
    private val baseUrl: () -> String?,
    private val cookieJar: CookieJar,
    errorBus: ApiErrorBus,
    private val accessCredentials: () -> AccessCredentials? = { null },
    private val basicAuthCredentials: () -> BasicAuthCredentials? = { null },
) {
    /**
     * Cloudflare Access 服务令牌或 Basic Auth 只发给当前后端所在的 HTTPS host；
     * 头像可能指向别的域名，不能把凭证带过去。
     */
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder()
        var authenticated = false
        RequestHeaders.accessHeaders(
            baseUrl = baseUrl(),
            requestUrl = request.url,
            credentials = accessCredentials(),
        )?.let { headers ->
            builder.header("CF-Access-Client-Id", headers.first)
            builder.header("CF-Access-Client-Secret", headers.second)
            authenticated = true
        }
        RequestHeaders.basicAuthHeader(
            baseUrl = baseUrl(),
            requestUrl = request.url,
            credentials = basicAuthCredentials(),
        )?.let { header ->
            builder.header("Authorization", header)
            authenticated = true
        }
        chain.proceed(if (authenticated) builder.build() else request)
    }

    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.header("X-Timezone") == null) {
            builder.header("X-Timezone", RequestHeaders.timezone())
        }
        if (RequestHeaders.isMutating(request.method)) {
            val base = baseUrl()
            if (base != null && request.header("Origin") == null) {
                builder.header("Origin", RequestHeaders.originFor(base))
            }
        }
        if (request.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }
        chain.proceed(builder.build())
    }

    private val errorInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            android.util.Log.w(
                "EidolonNative",
                "${response.code} ${chain.request().method} ${chain.request().url.encodedPath}",
            )
            val body = runCatching { response.peekBody(64L * 1024).string() }.getOrDefault("")
            errorBus.report(ApiError.from(response.code, body, response.header("Retry-After")))
        }
        response
    }

    private fun client(
        readTimeoutSeconds: Long,
        callTimeoutSeconds: Long,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(authInterceptor)
        .addInterceptor(headerInterceptor)
        .addInterceptor(errorInterceptor)
        .followRedirects(false)
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    val api: OkHttpClient = client(readTimeoutSeconds = 30, callTimeoutSeconds = 45)

    /** 自带密钥的试调/列模型可能很慢。 */
    val check: OkHttpClient = client(readTimeoutSeconds = 120, callTimeoutSeconds = 180)

    /** SSE：不设读超时和总超时，由协程取消来结束。 */
    val sse: OkHttpClient = client(readTimeoutSeconds = 0, callTimeoutSeconds = 0)

    /** 数据包：上传/下载可能持续几分钟，不能用普通 API 客户端的 45 秒总超时。 */
    val transfer: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(authInterceptor)
        .addInterceptor(headerInterceptor)
        .addInterceptor(errorInterceptor)
        .followRedirects(false)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    /** 头像等公开图片；不带 cookie/Origin，走 HTTP 缓存。 */
    val image: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .cache(Cache(File(context.cacheDir, "http-images"), 50L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
