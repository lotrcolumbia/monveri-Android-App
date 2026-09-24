package co.monveri.register.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class HostSwitchInterceptorTest {

    @Test
    fun `rewrites the placeholder host and path to the paired store, keeping the endpoint path`() {
        val interceptor = HostSwitchInterceptor(fakeProvider("https://monverisuite.com/stores/spaw66yk0wxk/api/register/"))
        val request = placeholderRequest("auth/employee-login.php")

        val rewritten = intercept(interceptor, request)

        assertEquals(
            "https://monverisuite.com/stores/spaw66yk0wxk/api/register/auth/employee-login.php",
            rewritten.url.toString(),
        )
    }

    @Test
    fun `preserves query parameters on the rewritten url`() {
        val interceptor = HostSwitchInterceptor(fakeProvider("https://monverisuite.com/stores/spaw66yk0wxk/api/register/"))
        val request = placeholderRequest("products/search.php?q=thread&limit=25")

        val rewritten = intercept(interceptor, request)

        assertEquals(
            "https://monverisuite.com/stores/spaw66yk0wxk/api/register/products/search.php?q=thread&limit=25",
            rewritten.url.toString(),
        )
    }

    @Test
    fun `leaves an already-absolute pairing request untouched`() {
        val interceptor = HostSwitchInterceptor(fakeProvider("https://monverisuite.com/stores/spaw66yk0wxk/api/register/"))
        val request = Request.Builder()
            .url("https://monverisuite.com/stores/spaw66yk0wxk/api/register/auth/validate-key.php")
            .build()

        val rewritten = intercept(interceptor, request)

        assertEquals(request.url, rewritten.url)
    }

    @Test
    fun `is a no-op before anything has been paired`() {
        val interceptor = HostSwitchInterceptor(fakeProvider(BaseUrlProvider.UNPAIRED_PLACEHOLDER))
        val request = placeholderRequest("auth/employee-login.php")

        val rewritten = intercept(interceptor, request)

        assertEquals(request.url, rewritten.url)
    }

    private fun fakeProvider(url: String) = object : BaseUrlProvider {
        override fun baseUrl() = url
    }

    private fun placeholderRequest(endpointPath: String): Request =
        Request.Builder().url(BaseUrlProvider.UNPAIRED_PLACEHOLDER + endpointPath).build()

    /** Runs the interceptor and returns the (possibly rewritten) request it forwarded. */
    private fun intercept(interceptor: HostSwitchInterceptor, request: Request): Request {
        val forwarded = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(forwarded)) } answers {
            Response.Builder()
                .request(forwarded.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        interceptor.intercept(chain)
        return forwarded.captured
    }
}
