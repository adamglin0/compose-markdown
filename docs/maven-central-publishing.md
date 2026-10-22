# Maven Central Publishing

## Published Modules

- `:markdown-core` -> `com.adamglin.compose.markdown:markdown-core`
- `:markdown-compose` -> `com.adamglin.compose.markdown:markdown-compose`
- `:sample-chat` and `:benchmarks` stay unpublished

## Required Setup

1. Create a namespace and user token in the Central Portal.
2. Make sure the `POM_*` metadata in `gradle.properties` matches the public repository you want to publish.
3. Export the credentials and signing values as Gradle project properties.

## Recommended Environment Variables

```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername="your-central-portal-token-username"
export ORG_GRADLE_PROJECT_mavenCentralPassword="your-central-portal-token-password"
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --export-secret-keys --armor <key-id>)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="your-gpg-password"
```

`signingInMemoryKey` must be the raw ASCII-armored private key (`-----BEGIN PGP PRIVATE KEY BLOCK-----` ...), not a base64-encoded copy of it.

## GitHub Actions Secrets

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `GPG_KEY_CONTENTS` (raw armored private key, as above)
- `SIGNING_PASSWORD`

`.github/workflows/publish.yml` reads these secrets and publishes when you push a `v*` tag (or run it manually with a version input). The release version comes from the tag (`v0.2.0` -> `0.2.0`) and overrides `VERSION_NAME` through the `releaseVersion` Gradle property. Before uploading, the workflow runs `:markdown-core:check` and `:markdown-compose:check`: the tests of every target, including the iOS simulator tests, and the ABI check. The sample app is not part of the publish gate.

## Publish Commands

- Local verification: `mise run publish-maven-local`
- Release upload and auto-release: `mise run publish-maven-central`
- CI release upload: push a `v*` tag or run `.github/workflows/publish.yml` manually

## Toolchain Requirements

- `markdown-core` targets Java 17 bytecode.
- `markdown-compose` targets Java 21 bytecode because `dev.snipme:highlights` (1.1.0) ships Java 21 class files; desktop JVM consumers of `markdown-compose` therefore need Java 21.
- Gradle runs on JDK 21 (`mise.toml`, CI); the JDK 17 toolchain is auto-provisioned through the foojay resolver if it is not installed.
- The publish workflow and the CI iOS job run on `macos-26`: Compose Multiplatform UIKit code (1.11 and later) references iOS 26 SDK symbols (`UIViewLayoutRegion`), so linking iOS test binaries fails with older Xcode versions.
- The libraries are built against Compose Multiplatform 1.12.1, which is therefore the minimum Compose version for consumers; Android consumers need `compileSdk` 37 or later, as Compose 1.12 requires. The library and `sample-chat` share one version on purpose: the Compose Gradle plugin is applied build-wide at a single version and unpacks the skiko wasm runtime matching that version, so compiling the libraries against a different Compose version than the plugin reproduces the skiko skew that silently broke the wasmJs browser tests. RaTeX (`io.github.darriousliu:ratex`) in `sample-chat` pins the Compose version it needs, so bump both together.

`automaticRelease` is enabled automatically for non-`-SNAPSHOT` versions.

## Versioning

- `0.y.z` while the API and parser behavior are still settling.
- Bump `y` for changes to the public API (any change to the ABI dumps that removes or changes a declaration) or to snapshot, delta or rendering semantics.
- Bump `z` for compatible fixes, additions, docs and performance work.

## ABI Validation

Both published modules use Kotlin explicit API mode and Kotlin's built-in ABI validation (`kotlin { abiValidation { ... } }`). The reference dumps are committed:

- `api/jvm/<module>.api` and `api/android/<module>.api`: JVM and Android class files;
- `api/<module>.klib.api`: the klib targets (iOS, JS, Wasm).

`checkKotlinAbi` compares the current code with the dumps, and `check` depends on it, so `ciCheck`, the publish workflow and `mise run publish-maven-local` all fail on an unrecorded API change. After an intended change, run `./gradlew updateKotlinAbi` and commit the updated dumps with the change. Neither module has platform-specific source sets, and Kotlin cross-compiles the iOS klibs on Linux, so every host produces the same dumps.

## Release Checklist

1. Set `VERSION_NAME` in `gradle.properties` to the release version.
2. Verify `README.md` install coordinates and release notes.
3. Run `mise run publish-maven-local`.
4. Commit the release version, create the `v<version>` tag, and push the commit plus tag.
5. Confirm the deployment in Central Portal if you disabled auto-release.
