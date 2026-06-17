# Spectra

**Meta's glasses SDK, minus the boilerplate and most of the suffering.**

Spectra is a Kotlin Multiplatform wrapper around [Meta's Wearables Device Access Toolkit](https://wearables.developer.meta.com/docs/develop/dat/) (DAT). It collapses the native SDK — callbacks, result builders, activity-result contracts — into one coroutine-and-`Flow` API you call from `commonMain`. Registration, permissions, device sessions, camera streaming and photo capture, all behind a single `SpectraClient`.

> Independent wrapper. Not affiliated with, sponsored by, or endorsed by Meta. The toolkit is a developer preview, so the API will move.

## What's in here

```
demo/
  spectra/      THE LIBRARY (KMP, publishable). Agnostic of the demo:
                commonMain (core/camera/display/mock + Spectra/SpectraClient),
                androidMain (real mwdat backend), iOS targets.
  shared/       Playground UI (Compose). Depends on :spectra — like any app would.
  androidApp/   Android entry point.
  iosApp/       iOS Xcode project (mock for now; Swift bridge pending).
docs/           Spectra Docs — a static, Vercel-ready site (index.html + llms.txt + AGENTS.md).
```

The library (`:spectra`) is standalone: it has no dependency on the demo, so any project can use it. The demo is just the first consumer. It has a runtime **Backend** toggle — **Mock** (synthetic frames, no hardware) or **Glasses (real)** (talks to the Meta AI app and your glasses); Android ships the real backend wired, iOS runs on the mock until the Swift bridge is added.

## Use Spectra in your own project

Publish the library to Maven Local, then depend on it from any KMP project — no demo required:

```bash
cd demo && ./gradlew :spectra:publishToMavenLocal   # -> com.umain.spectra:spectra:0.1.0
```

```kotlin
// settings.gradle.kts of your project — add mavenLocal() and the mwdat repo
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google(); mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials { username = ""; password = providers.gradleProperty("github_token").orNull ?: System.getenv("GH_TOKEN") ?: "" }
            content { includeGroup("com.meta.wearable") }
        }
    }
}

// shared/build.gradle.kts of your project
kotlin { sourceSets { commonMain.dependencies { implementation("com.umain.spectra:spectra:0.1.0") } } }
```

Then `Spectra.mock()` works anywhere; `Spectra.create(context, bridge)` gives the real Android client (needs the GitHub token + the manifest credentials).

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
