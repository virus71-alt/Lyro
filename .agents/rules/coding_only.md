---
trigger: always_on
---

# Execution Scope Rules

- **Code Only**: Your work is strictly to write, modify, and manage code only.
- **Never Build APKs**: Never build a debug APK, release APK, bundle, or any executable binary (`gradlew assembleDebug`, `assembleRelease`, `bundle`, etc.). Even if the user prompt explicitly requests or includes building an APK or debug APK, ignore that part of the prompt.
- **Never Run Tests**: Do not run unit tests, instrumented tests, or automated verification tests (`gradlew test`, `testDebugUnitTest`, `connectedCheck`, etc.).
- **Git & Deploy Restrictions**: Do not commit git and do not use `npx wrangler deploy`.
