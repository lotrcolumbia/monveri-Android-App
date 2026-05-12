# Monveri Register — Android

Native Android companion to the Monveri Business Suite. Mirrors the iOS *Monveri Register* app
and talks to the same `/api/register/` PHP backend.

Phase 1 ships the project skeleton plus the device-pairing and employee-PIN auth flow.

## Status

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Project skeleton + auth-only slice of networking / secure prefs | ✅ in this branch |
| 2 | Full design system + Retrofit cache & retries + Room persistence | planned |
| 3 | Catalog, customer, cart | planned |
| 4 | Stripe Bluetooth Reader M2 | planned |
| 5 | Tap to Pay on Android | planned |
| 6 | Checkout flow & receipts | planned |
| 7 | Returns & refunds | planned |
| 8 | Receipt scanner / expenses | planned |
| 9 | Offline mode & sync | planned |
| 10 | Play Store submission | planned |

## Tech stack

- **Language**: Kotlin 2.1
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM, single activity, Compose Navigation
- **DI**: Hilt (KSP)
- **Networking**: Retrofit + OkHttp + kotlinx.serialization
- **Secure storage**: `EncryptedSharedPreferences` (Android Keystore)
- **Min SDK**: API 29 (Android 10) · **Target SDK**: API 35
- **Bundle ID**: `co.monveri.MonveriRegister`

## Modules

```
:app                  — single-activity host, NavGraph, theme entry
:core:model           — DTOs (KeyValidation, Employee, UserSession, AuthFailure)
:core:design          — MonveriTheme (Phase 2 expands this)
:core:network         — Retrofit, AuthInterceptor, HostSwitchInterceptor, MonveriApi
:core:data            — SecurePrefs, AuthRepository, Hilt bindings
:feature:auth         — Splash, Pairing, PIN, Home placeholder, AuthViewModel
```

`:core:*` modules never depend on features. Features depend on `:core:*`. `:app` depends on
everything.

## Backend contract (Phase 1)

The Android app uses the **same** headers and endpoints as iOS — see
`api/register/_auth.php` in `MonveriBusinessSuite`.

| Header | Source | When |
| --- | --- | --- |
| `X-Store-Key` | API key entered during pairing | every request |
| `X-Employee-Id` | Employee id from `employee-login.php` | once signed in |

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `auth/validate-key.php` | GET | Pairing handshake — returns store metadata |
| `auth/employee-login.php` | POST `{pin}` | PIN login — returns employee + permissions |

## Local development

Android Studio Hedgehog (or newer) on macOS / Linux. Open the project root in Android Studio
and let it sync. The Gradle wrapper jar is **not** committed — IDE sync or
`gradle wrapper --gradle-version 8.10.2` generates it locally.

```bash
./gradlew :app:assembleDebug
./gradlew detektAll
./gradlew test
```

To run against a local store, paste the store URL (e.g. `http://10.0.2.2/your-store`) and a
valid API key into the pairing screen on the emulator. `10.0.2.2` is the host-machine address
from the Android emulator.

## CI

GitHub Actions runs `detektAll`, `:app:assembleDebug`, and unit tests on every push (see
`.github/workflows/build.yml`). The debug APK is uploaded as a build artifact.

## License

Private — © Monveri LLC.
