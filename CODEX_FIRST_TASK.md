# First Codex task — make v0.1 compile and run

Work directly in this repository.

Goal: turn the existing Mod Detective v0.1 engine into a clean, compilable, launchable Minecraft 1.21.1 NeoForge client-side mod on Java 21.

Do not add the full GUI yet.

Tasks:
1. Inspect the whole repository, including build.gradle, gradle.properties, neoforge.mods.toml and all Java sources.
2. Verify the project configuration against the current NeoForge 1.21.1 APIs available to this project.
3. Ensure a working Gradle wrapper is present. If the repository was based on a partial MDK and wrapper files are missing, restore/generate the standard wrapper in the safest normal way available in the environment.
4. Run the build and fix every compilation/configuration error.
5. Preserve these features: mod snapshot/diff, 30-second Black Box, freeze detector, render-thread watchdog, stack-to-mod attribution, ranked suspects, JSON incident reports.
6. Check thread safety and obvious lifecycle errors, especially around Minecraft client startup/shutdown and access to the render thread.
7. Make client-only registration safe so the mod does not accidentally load client classes in an inappropriate environment.
8. Add small focused tests for pure Java logic where useful (for example freeze thresholding, snapshot diffing, suspect ranking) if the project setup supports them cleanly.
9. Run the build/tests again.
10. If possible, launch runClient far enough to verify Mod Detective loads. Inspect the log for exceptions originating from the mod.

Do not hide failures. If Minecraft cannot be launched because of environment limitations, leave the repository buildable and state exactly what remains unverified.

When done, provide a concise engineering report with files changed, commands executed, build/test status, runtime validation status, and remaining risks.
