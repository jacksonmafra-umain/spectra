# Spectra

**Meta's glasses SDK, minus the boilerplate and most of the suffering.**

Spectra is a Kotlin Multiplatform wrapper around [Meta's Wearables Device Access Toolkit](https://wearables.developer.meta.com/docs/develop/dat/) (DAT). It collapses the native SDK — callbacks, result builders, activity-result contracts — into one coroutine-and-`Flow` API you call from `commonMain`. Registration, permissions, device sessions, camera streaming and photo capture, all behind a single `SpectraClient`.

> Independent wrapper. Not affiliated with, sponsored by, or endorsed by Meta. The toolkit is a developer preview, so the API will move.

## What's in here

```
demo/    Spectra Playground — a Compose Multiplatform app (Android + iOS).
         The Spectra library source lives in demo/shared (com.umain.spectra.*):
           shared/      the library + the Playground UI
           androidApp/  Android entry point (real mwdat backend)
           iosApp/      iOS Xcode project (mock for now; Swift bridge pending)
docs/    Spectra Docs — a static, Vercel-ready site (index.html + llms.txt + AGENTS.md).
```

The demo has a runtime **Backend** toggle: **Mock** (synthetic frames, no hardware, runs anywhere) or **Glasses (real)** (talks to the Meta AI app and your glasses). Android ships the real backend wired; iOS runs on the mock until the Swift bridge is added.

## Run the demo

Requires JDK 17+, Android SDK, and (for iOS) Xcode. Use the Gradle wrapper inside `demo/`.

**Android**

```bash
cd demo
./gradlew :androidApp:installDebug      # launches "Spectra"
```

The mock needs no token. To use **Glasses (real)** on Android, put a GitHub Packages token in `demo/local.properties` (`github_token=ghp_...`, scope `read:packages`) so the `mwdat-*` artifacts resolve, and enable Developer Mode in the Meta AI app.

**iOS**

Open `demo/iosApp/Spectra.xcodeproj` in Xcode, pick a simulator (Apple Silicon), and Run. The `Shared` framework is built automatically by a Gradle build phase.

## Use it on real glasses

The full Meta-side setup — Developer Center, app registration, credentials, release channels, glasses/Developer Mode, and the iOS Universal Link — is documented step by step in the docs site: open `docs/index.html`, section **"Go live: wiring it to Meta AI and the glasses."**

## Documentation

`docs/` deploys to Vercel with the root directory set to `docs` and no build step. It ships `llms.txt` and `AGENTS.md` so coding agents can write correct integrations. See `docs/README.md`.

## License

MIT. See [LICENSE](LICENSE).
