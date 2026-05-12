package co.monveri.register.feature.auth

import app.cash.turbine.test
import co.monveri.register.data.AuthRepository
import co.monveri.register.model.AuthFailure
import co.monveri.register.model.AuthState
import co.monveri.register.model.Employee
import co.monveri.register.model.KeyValidation
import io.mockk.Runs
import io.mockk.coEvery
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
    fun `pair surfaces invalid-key failure as error message`() = runTest(dispatcher) {
        coEvery { repository.pair(any(), any()) } throws AuthFailure.InvalidKey("Invalid or inactive API key")
        val vm = AuthViewModel(repository)
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
        coEvery { repository.pair(any(), any()) } returns KeyValidation(
            success = true,
            storeName = "Carolina Thread Place",
            storeCode = "CTP",
        )
        val vm = AuthViewModel(repository)
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
        val vm = AuthViewModel(repository)

        vm.pair()
        dispatcher.scheduler.advanceUntilIdle()

        vm.pairing.test {
            val state = expectMostRecentItem()
            assertNotNull(state.errorMessage)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun `four pin digits auto-submit`() = runTest(dispatcher) {
        coEvery { repository.login("1234") } returns Employee(
            id = 42,
            name = "Will Jeffcoat-McLeod",
            username = "wjeffcoatmcleod",
            level = 1,
            status = "active",
        )
        val vm = AuthViewModel(repository)

        listOf('1', '2', '3', '4').forEach(vm::onPinDigit)
        dispatcher.scheduler.advanceUntilIdle()

        vm.login.test {
            val state = expectMostRecentItem()
            assertNotNull(state.employee)
            assertEquals(42, state.employee?.id)
        }
    }

    @Test
    fun `invalid pin surfaces error and clears submission`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } throws AuthFailure.InvalidPin("Invalid PIN")
        val vm = AuthViewModel(repository)

        listOf('9', '9', '9', '9').forEach(vm::onPinDigit)
        dispatcher.scheduler.advanceUntilIdle()

        vm.login.test {
            val state = expectMostRecentItem()
            assertNull(state.employee)
            assertEquals("Invalid PIN", state.errorMessage)
        }
    }

    @Test
    fun `logout delegates to repository and clears login state`() = runTest(dispatcher) {
        every { repository.logout() } just Runs
        val vm = AuthViewModel(repository)
        vm.onPinDigit('1')

        vm.logout()
        dispatcher.scheduler.advanceUntilIdle()

        verify { repository.logout() }
        vm.login.test { assertTrue(expectMostRecentItem().pin.isEmpty()) }
    }
}
