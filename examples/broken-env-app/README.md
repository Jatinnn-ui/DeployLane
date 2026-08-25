# broken-env-app

Deliberately fails at startup because a required environment variable is missing. Used to demonstrate
DeployForge's failure analysis and the fix-and-redeploy loop.

This is a **real** failure, not a simulated one: the process validates its configuration, writes a
diagnostic to stderr and exits with code 1, exactly as a database client would.

## The demonstration

1. Import this app and deploy it **without** setting any environment variables.
2. The pipeline gets as far as `HEALTH_CHECKING`, then fails. DeployForge notices the container exited
   rather than waiting out the full timeout, and pulls the container's own output into the deployment log:

   ```
   APPLICATION ERROR  FATAL: required environment variable DATABASE_URL is not set
   APPLICATION ERROR  Cannot initialise the database client without a connection string.
   ```

3. The analysis card reports something like:

   > **Root cause** — The required environment variable `DATABASE_URL` is not configured for this
   > environment.
   >
   > **Recommended fix** — Open Project → Environment, add `DATABASE_URL`, then redeploy.
   >
   > **Confidence** — high

   With `AI_PROVIDER=heuristic` (the default) this comes from the built-in rule based analyzer and needs
   no API key. With `gemini` or `openai` configured, the same evidence goes to the model instead.

4. Add `DATABASE_URL` under **Environment** — any value shaped like a URL works, for example
   `postgres://demo:demo@localhost:5432/demo`.
5. Press **Redeploy**. The same commit now passes its health check and goes live.

## What it exercises

- Container crash detection during health checking (fail fast instead of burning the timeout)
- Application stdout/stderr capture as failure evidence
- Named-variable root cause analysis
- The fix → redeploy → success loop
- Secret hygiene: the app prints the connection *scheme*, never the URL, and DeployForge redacts the
  value if anything else prints it
