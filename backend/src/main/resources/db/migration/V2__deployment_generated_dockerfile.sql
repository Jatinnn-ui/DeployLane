-- The Dockerfile DeployForge generated for a deployment is build metadata: it makes a build
-- reproducible and reviewable, and it is real evidence for AI failure analysis.
-- Stored per deployment rather than written into the user's repository.
ALTER TABLE deployments
    ADD COLUMN generated_dockerfile TEXT;
