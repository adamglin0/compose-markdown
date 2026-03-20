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

If your key needs it, also set `ORG_GRADLE_PROJECT_signingInMemoryKeyId`.

## Publish Commands

- Snapshot upload: `./gradlew publishToMavenCentral`
- Release upload and auto-release: `./gradlew publishToMavenCentral`
- Local verification: `./gradlew publishToMavenLocal`

`automaticRelease` is enabled automatically for non-`-SNAPSHOT` versions.

## Release Checklist

1. Change `version` in `build.gradle.kts` from `-SNAPSHOT` to the release version.
2. Verify `README.md` install coordinates and release notes.
3. Run `./gradlew test publishToMavenLocal`.
4. Run `./gradlew publishToMavenCentral` with Central credentials and signing key loaded.
5. Confirm the deployment in Central Portal if you disabled auto-release.
