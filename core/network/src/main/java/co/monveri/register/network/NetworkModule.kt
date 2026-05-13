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
            // Never log auth header values — they're the entire access credential.
            redactHeader(AuthHeaders.STORE_KEY)
            redactHeader(AuthHeaders.EMPLOYEE_ID)
        }

    @Provides
    @Singleton
    fun provideOkHttpCache(@ApplicationContext context: Context): Cache =
        Cache(File(context.cacheDir, "http"), CACHE_BYTES)

    /** Adds `Cache-Control: max-age=60` to GET responses so OkHttp's cache actually populates. */
    @Provides
    @Singleton
    fun provideCacheControlInterceptor(): Interceptor = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        if (chain.request().method == "GET" && response.isSuccessful) {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=$CACHE_MAX_AGE_SECONDS")
                .build()
        } else {
            response
        }
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
