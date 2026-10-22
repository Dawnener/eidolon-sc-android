package sc.eidolon.nativeapp

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class EidolonApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 头像也要带 Cloudflare Access 服务令牌，跟 /api 用同一个 HTTP 客户端。
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { container.http.image }))
                }
                .build()
        }
        container.start()
    }
}
