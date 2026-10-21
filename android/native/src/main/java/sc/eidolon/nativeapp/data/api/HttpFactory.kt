package sc.eidolon.nativeapp.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class HttpFactory(
    context: Context,
    private val baseUrl: () -> String?,
    private val cookieJar: CookieJar,
    errorBus: ApiErrorBus,
) {
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

    /** 头像等公开图片；不带 cookie/Origin，走 HTTP 缓存。 */
    val image: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http-images"), 50L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
