# Parameter binding fix — 06/10/2026

## Observed cause

The screenshot reports Spring's missing method parameter names for `java.lang.String`. The deployed VM used Ubuntu Maven 3.8.7 and its default `maven-compiler-plugin:3.1`. That plugin predates support for the existing `<parameters>true</parameters>` configuration. `javap` on the deployed UserProfileController showed zero MethodParameters entries; runtime logs contained the matching binding error.

## Changes and verification

- Root POM pins compiler plugin 3.13.0 and enables `maven.compiler.parameters`, retaining the explicit parameters configuration.
- Surefire is pinned to 3.2.5 so JUnit 5 executes consistently with Maven 3.8 and 3.9.
- Added MockMvc regression requests for unnamed path and query parameters on the real UserProfileController.
- Focused reactor test (`common-lib` plus `user-service`) passed: 2 tests, 0 failures/errors/skips.
- Linux source build now uses `clean package` to prevent retaining old bytecode without metadata.

The owner initially requested that the VM be stopped to save credit, then authorized one temporary startup to install and verify automatic deployment. The VM built and deployed the fix from release `89b4e74e592ba3dc80e1cbaf273990cf641d2a7e`. The deployed UserProfileController bytecode contains MethodParameters metadata, and the real internal post batch route with unnamed String query parameters returned HTTP 200 with an empty result. Both checks passed again after reboot. This verifies the compiler metadata fix; an authenticated browser walkthrough of all features remains separate. The VM was deallocated again after verification.
