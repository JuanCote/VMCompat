# Contributing to VMCompat

Fork this repository, create a branch and open a pull request against `main`. Include the dependency versions, macOS version, Mac model, reproduction steps and test results. Keep changes focused and include the source rather than a compiled mod.

## Loader compatibility

The current loader boundary is `src/main/java/vmcompat/WorkshopBootstrap.java`. For a Storm port, identify the exact Storm and Viewpoint builds and show when VMCompat initializes relative to the render classes it patches. Keep the shared OpenGL compatibility path reusable.

Test a local or authorized multiplayer server and document the prerequisites, first-person rendering, and any remaining caveats. Run the existing ZombieBuddy fixture checks as well so maintainers can review regressions in the Workshop configuration.

Workshop publishing is handled separately after review and in-game testing. Please keep third-party JARs, logs, saves, Workshop caches and signing material outside the repository.
