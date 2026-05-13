package co.monveri.register.network

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 30L
    private const val CACHE_BYTES = 10L * 1024 * 1024 // 10 MB
    private const val CACHE_MAX_AGE_SECONDS = 60

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideNetworkErrorMapper(json: Json): NetworkErrorMapper = NetworkErrorMapper(json)

    @Provides
    @Singleton
    @DebugBuild
    fun provideIsDebugBuild(): Boolean = BuildConfig.DEBUG

    @Provides
    @Singleton
    fun provideLoggingInterceptor(@DebugBuild isDebug: Boolean): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (isDebug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            // Never log auth header values — they're the entire access credential. We don't
            // currently emit `Authorization`, but redacting it defensively means a future
            // bearer-flow can't accidentally leak tokens through logcat.
            redactHeader(AuthHeaders.STORE_KEY)
            redactHeader(AuthHeaders.EMPLOYEE_ID)
            redactHeader("Authorization")
        }

    @Provides
    @Singleton
    fun provideOkHttpCache(@ApplicationContext context: Context): Cache =
        Cache(File(context.cacheDir, "http"), CACHE_BYTES)

    /**
     * Hints OkHttp's disk cache to keep successful GETs for [CACHE_MAX_AGE_SECONDS].
     *
     * Constraints honoured:
     *  - If the server already returned `no-store`, `no-cache`, or `private`, leave it alone —
     *    the server's directive wins.
     *  - Authenticated requests (those carrying `X-Store-Key`) get a `private` cache directive
     *    so shared/intermediary caches never store the response. Every request from this app is
     *    authenticated in practice, but we check explicitly to keep this interceptor reusable.
     *  - Non-GET methods are passed through untouched.
     */
    @Provides
    @Singleton
    fun provideCacheControlInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val response: Response = chain.proceed(request)

        if (request.method != "GET" || !response.isSuccessful) {
            return@Interceptor response
        }

        val existing = response.header("Cache-Control").orEmpty().lowercase()
        if (
            existing.contains("no-store") ||
            existing.contains("no-cache") ||
            existing.contains("private")
        ) {
            return@Interceptor response
        }

        val isAuthenticated = request.header(AuthHeaders.STORE_KEY) != null ||
            request.header("Authorization") != null
        val directive = if (isAuthenticated) {
            "private, max-age=$CACHE_MAX_AGE_SECONDS"
        } else {
            "max-age=$CACHE_MAX_AGE_SECONDS"
        }

        response.newBuilder()
            .header("Cache-Control", directive)
            .build()
    }

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        hostSwitchInterceptor: HostSwitchInterceptor,
        retryInterceptor: RetryInterceptor,
        cacheControlInterceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        cache: Cache,
    ): OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(hostSwitchInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(retryInterceptor)
        .addNetworkInterceptor(cacheControlInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BaseUrlProvider.UNPAIRED_PLACEHOLDER)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideMonveriApi(retrofit: Retrofit): MonveriApi = retrofit.create(MonveriApi::class.java)
}
