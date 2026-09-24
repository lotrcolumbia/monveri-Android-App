package co.monveri.register.feature.auth

import app.cash.turbine.test
import co.monveri.register.data.AuthRepository
import co.monveri.register.data.repository.RegisterSessionRepository
import co.monveri.register.model.AuthState
import co.monveri.register.model.Employee
import co.monveri.register.model.KeyValidation
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: AuthRepository = mockk(relaxed = true)
    private val registerSessionRepository: RegisterSessionRepository = mockk(relaxed = true)
    private val stateFlow = MutableStateFlow(AuthState.Unpaired)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.state } returns stateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pair surfaces NetworkResult Failure as error message`() = runTest(dispatcher) {
        coEvery { repository.pair(any(), any()) } returns
            NetworkResult.Failure(NetworkError.Unauthorized("Invalid or inactive API key"))
        val vm = AuthViewModel(repository, registerSessionRepository)
        vm.onPairingBaseUrlChanged("https://store.example")
        vm.onPairingApiKeyChanged("bad-key")

        vm.pair()
        dispatcher.scheduler.advanceUntilIdle()

        vm.pairing.test {
            val state = expectMostRecentItem()
            assertEquals("Invalid or inactive API key", state.errorMessage)
            assertEquals(false, state.isLoading)
            assertNull(state.pairedStoreName)
        }
    }

    @Test
    fun `pair success captures store name`() = runTest(dispatcher) {
        coEvery { repository.pair(any(), any()) } returns NetworkResult.Success(
            KeyValidation(
                success = true,
                storeName = "Carolina Thread Place",
                storeCode = "CTP",
            ),
        )
        val vm = AuthViewModel(repository, registerSessionRepository)
        vm.onPairingBaseUrlChanged("https://store.example")
        vm.onPairingApiKeyChanged("good-key")

        vm.pair()
        dispatcher.scheduler.advanceUntilIdle()

        vm.pairing.test {
            assertEquals("Carolina Thread Place", expectMostRecentItem().pairedStoreName)
        }
    }

    @Test
    fun `pair rejects empty inputs without calling repository`() = runTest(dispatcher) {
        val vm = AuthViewModel(repository, registerSessionRepository)

        vm.pair()
        dispatcher.scheduler.advanceUntilIdle()

        vm.pairing.test {
            val state = expectMostRecentItem()
            assertNotNull(state.errorMessage)
            assertEquals(false, state.isLoading)
        }
        coVerify(exactly = 0) { repository.pair(any(), any()) }
    }

    @Test
    fun `eight pin digits auto-submit`() = runTest(dispatcher) {
        coEvery { repository.login("12345678") } returns NetworkResult.Success(
            Employee(
                id = 42,
                name = "Will Jeffcoat-McLeod",
                username = "wjeffcoatmcleod",
                level = 1,
                status = "active",
            ),
        )
        val vm = AuthViewModel(repository, registerSessionRepository)

        listOf('1', '2', '3', '4', '5', '6', '7', '8').forEach(vm::onPinDigit)
        dispatcher.scheduler.advanceUntilIdle()

        vm.login.test {
            val state = expectMostRecentItem()
            assertNotNull(state.employee)
            assertEquals(42, state.employee?.id)
        }
    }

    @Test
    fun `invalid pin surfaces error and clears submission`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns
            NetworkResult.Failure(NetworkError.Unauthorized("Invalid PIN"))
        val vm = AuthViewModel(repository, registerSessionRepository)

        listOf('9', '9', '9', '9', '9', '9', '9', '9').forEach(vm::onPinDigit)
        dispatcher.scheduler.advanceUntilIdle()

        vm.login.test {
            val state = expectMostRecentItem()
            assertNull(state.employee)
            assertEquals("Invalid PIN", state.errorMessage)
        }
    }

    @Test
    fun `offline pin surfaces network error message`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns
            NetworkResult.Failure(NetworkError.Offline())
        val vm = AuthViewModel(repository, registerSessionRepository)

        listOf('1', '2', '3', '4', '5', '6', '7', '8').forEach(vm::onPinDigit)
        dispatcher.scheduler.advanceUntilIdle()

        vm.login.test {
            val state = expectMostRecentItem()
            assertNull(state.employee)
            assertEquals("No internet connection", state.errorMessage)
        }
    }

    @Test
    fun `onQrScanned populates pending payload and prefills form fields`() = runTest(dispatcher) {
        val vm = AuthViewModel(repository, registerSessionRepository)
        val raw = "monveri://pair?v=1&url=https%3A%2F%2Fstore.example&key=abc123&name=Store%20Name"

        vm.onQrScanned(raw)

        vm.pairing.test {
            val state = expectMostRecentItem()
            assertNotNull(state.pendingPayload)
            assertEquals("https://store.example", state.baseUrl)
            assertEquals("abc123", state.apiKey)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `onQrScanned rejects an unrecognized string without touching form fields`() = runTest(dispatcher) {
        val vm = AuthViewModel(repository, registerSessionRepository)

        vm.onQrScanned("not a pairing code")

        vm.pairing.test {
            val state = expectMostRecentItem()
            assertNull(state.pendingPayload)
            assertNotNull(state.errorMessage)
            assertEquals("", state.baseUrl)
        }
    }

    @Test
    fun `dismissPendingPayload resets to a blank pairing form`() = runTest(dispatcher) {
        val vm = AuthViewModel(repository, registerSessionRepository)
        vm.onQrScanned("monveri://pair?v=1&url=https%3A%2F%2Fstore.example&key=abc123")

        vm.dismissPendingPayload()

        vm.pairing.test {
            val state = expectMostRecentItem()
            assertNull(state.pendingPayload)
            assertEquals("", state.baseUrl)
            assertEquals("", state.apiKey)
        }
    }

    @Test
    fun `needsRegisterOpen is true when no session is open`() = runTest(dispatcher) {
        coEvery { registerSessionRepository.current() } returns NetworkResult.Success(null)
        val vm = AuthViewModel(repository, registerSessionRepository)

        assertTrue(vm.needsRegisterOpen())
    }

    @Test
    fun `needsRegisterOpen fails open on a network error`() = runTest(dispatcher) {
        coEvery { registerSessionRepository.current() } returns
            NetworkResult.Failure(NetworkError.Offline())
        val vm = AuthViewModel(repository, registerSessionRepository)

        assertEquals(false, vm.needsRegisterOpen())
    }

    @Test
    fun `logout delegates to repository and clears login state`() = runTest(dispatcher) {
        every { repository.logout() } just Runs
        val vm = AuthViewModel(repository, registerSessionRepository)
        vm.onPinDigit('1')

        vm.logout()
        dispatcher.scheduler.advanceUntilIdle()

        verify { repository.logout() }
        vm.login.test { assertTrue(expectMostRecentItem().pin.isEmpty()) }
    }
}
