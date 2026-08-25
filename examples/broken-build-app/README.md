# broken-build-app

Deliberately fails during the **image build**, not at runtime. Used to demonstrate that DeployForge
distinguishes a build failure from a startup failure and explains each differently.

The failure is genuine and extremely common: `build.js` imports `slugify`, but `slugify` is not listed in
`dependencies`. It usually works on a developer machine because the package is still sitting in a stale
`node_modules`, and it always fails in a clean container build.

## The demonstration

1. Import this app and deploy it. Detection produces `npm install` + `npm run build` + `npm start`.
2. The pipeline reaches `IMAGE_BUILDING` and fails there. The build output streams into the log while it
   happens:

   ```
   BUILD   > preparing assets
   BUILD   > resolving modules
   BUILD   Error: Cannot find module 'slugify'
   DOCKER  The command '/bin/sh -c npm run build' returned a non-zero code: 1
   ```

3. The analysis card reports something like:

   > **Root cause** — The dependency `slugify` is not installed in the image. It is imported at runtime
   > but is missing from the installed dependencies.
   >
   > **Recommended fix** — Add `slugify` to `dependencies`.
   >
   > **Command** — `npm install slugify --save`

4. Fix it either way: add the dependency, or remove the import from `build.js`. Redeploy and the build
   passes.

## What it exercises

- Build output streaming while `docker build` is running, with build output separated from Docker's own
  progress lines in the log viewer's source filter
- Failure attribution to the `IMAGE_BUILD` step on the timeline, with the previous deployment untouched
- Non-zero exit code capture
- Module-level root cause analysis with a concrete, copyable command
