# Example applications

Three small applications used to demonstrate DeployForge end to end. One works, two fail on purpose -
and they fail at different stages, which is the point.

| Example                                  | Fails at         | Demonstrates                                              |
| ---------------------------------------- | ---------------- | --------------------------------------------------------- |
| [demo-node-app](./demo-node-app)         | -                | A successful deployment: clone, build, run, health check   |
| [broken-build-app](./broken-build-app)   | `IMAGE_BUILDING` | Build failure analysis, undeclared dependency              |
| [broken-env-app](./broken-env-app)       | `HEALTH_CHECKING`| Startup failure analysis, missing environment variable     |

Every failure is real. Nothing is stubbed, no analysis is pre-written: each app genuinely breaks, its own
output is captured, and the analyzer works from that output.

## Using them

The apps are plain directories here. To deploy one, push it to a GitHub repository - either as the
repository root, or keep this layout and set **Root directory** to `examples/demo-node-app` when
importing, which also exercises monorepo support.

They have no dependencies (except the intentionally missing one), so builds take seconds and cannot fail
for reasons unrelated to the platform.

## Suggested demo order

1. **demo-node-app** - watch the log stream, open the deployed URL.
2. **broken-build-app** - watch it fail at the build step, read the analysis, fix, redeploy.
3. **broken-env-app** - watch it fail at the health check, add the variable, redeploy.
4. Back on **demo-node-app**, deploy again and then **roll back** to the previous deployment to see the
   image being reused and traffic switching only after the new container is healthy.
