package com.deployforge.git;

import com.deployforge.runtime.LogSink;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Clones a repository into a deployment's working directory using JGit.
 *
 * <p>JGit rather than the {@code git} CLI on purpose:
 *
 * <ul>
 *   <li>no process spawning, so the OAuth token never appears in a command line or process list
 *   <li>no dependency on a git binary being installed in the container that runs DeployForge
 *   <li>progress and cancellation are first class, which the pipeline needs for live logs and cancel
 * </ul>
 *
 * <p>Clones are shallow ({@code depth 1}) and single branch: a deployment only ever needs one commit,
 * and full history on a large repository is a minutes-long download.
 */
@Component
public class GitCloneService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneService.class);

    /**
     * @param cloneUrl HTTPS clone URL
     * @param branch short branch name
     * @param accessToken GitHub token, or {@code null} for a public repository
     */
    public record CloneRequest(
            String cloneUrl,
            String branch,
            String accessToken,
            Path destination,
            Duration timeout,
            LogSink logSink) {}

    public record CloneResult(
            String commitSha,
            String commitMessage,
            String commitAuthor,
            Instant committedAt,
            long durationMs) {}

    public CloneResult clone(CloneRequest request) {
        long start = System.nanoTime();
        LogSink sink = request.logSink();

        var credentials =
                request.accessToken() == null || request.accessToken().isBlank()
                        ? null
                        // GitHub accepts the token as the username with any non-empty password.
                        : new UsernamePasswordCredentialsProvider(request.accessToken(), "x-oauth-basic");

        try (Git git =
                Git.cloneRepository()
                        .setURI(request.cloneUrl())
                        .setDirectory(request.destination().toFile())
                        .setBranch(request.branch())
                        .setBranchesToClone(List.of("refs/heads/" + request.branch()))
                        .setCloneAllBranches(false)
                        .setCloneSubmodules(false)
                        .setDepth(1)
                        .setTimeout((int) Math.max(30, request.timeout().toSeconds()))
                        .setCredentialsProvider(credentials)
                        .setProgressMonitor(new SinkProgressMonitor(sink))
                        .call()) {

            var commits = git.log().setMaxCount(1).call().iterator();
            if (!commits.hasNext()) {
                throw new GitCloneException(
                        "Branch '" + request.branch() + "' has no commits to deploy", false, false);
            }
            RevCommit head = commits.next();
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            String message = head.getShortMessage();
            String author = head.getAuthorIdent() == null ? null : head.getAuthorIdent().getName();
            Instant committedAt = Instant.ofEpochSecond(head.getCommitTime());

            log.info(
                    "clone_completed branch={} commit={} duration_ms={}",
                    request.branch(),
                    head.getName().substring(0, 7),
                    durationMs);

            return new CloneResult(head.getName(), message, author, committedAt, durationMs);

        } catch (CanceledException e) {
            throw new GitCloneException("Clone cancelled", false, true);
        } catch (InvalidRemoteException e) {
            throw new GitCloneException(
                    "The repository could not be found. It may have been renamed, deleted or made private.",
                    false,
                    false);
        } catch (TransportException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
            boolean authFailure =
                    message.contains("authentication")
                            || message.contains("not authorized")
                            || message.contains("401")
                            || message.contains("403");
            if (authFailure) {
                throw new GitCloneException(
                        "GitHub refused the clone. Reconnect GitHub and make sure the 'repo' scope is granted for private repositories.",
                        true,
                        false);
            }
            throw new GitCloneException("Clone failed: " + firstLine(e.getMessage()), false, false);
        } catch (GitAPIException e) {
            if (isCancelled(e)) {
                throw new GitCloneException("Clone cancelled", false, true);
            }
            throw new GitCloneException("Clone failed: " + firstLine(e.getMessage()), false, false);
        } catch (RuntimeException e) {
            if (isCancelled(e)) {
                throw new GitCloneException("Clone cancelled", false, true);
            }
            throw new GitCloneException("Clone failed: " + firstLine(e.getMessage()), e);
        }
    }

    private boolean isCancelled(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CanceledException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        String line = message.lines().findFirst().orElse(message).trim();
        return line.length() > 300 ? line.substring(0, 300) + "..." : line;
    }

    /**
     * Bridges JGit progress into the deployment log, and JGit cancellation into the pipeline's cancel
     * flag.
     *
     * <p>Only task starts and completions are reported. JGit emits progress updates per object, which
     * would drown the log viewer.
     */
    private static final class SinkProgressMonitor implements ProgressMonitor {

        private final LogSink sink;
        private String currentTask;
        private int totalWork;
        private int completed;

        private SinkProgressMonitor(LogSink sink) {
            this.sink = sink;
        }

        @Override
        public void start(int totalTasks) {
            emit("Starting clone");
        }

        @Override
        public void beginTask(String title, int total) {
            currentTask = title;
            totalWork = total;
            completed = 0;
            emit(title + (total > 0 ? " (" + total + " objects)" : ""));
        }

        @Override
        public void update(int completedUnits) {
            completed += completedUnits;
        }

        @Override
        public void endTask() {
            if (currentTask != null) {
                emit(
                        currentTask
                                + " done"
                                + (totalWork > 0 ? " (" + Math.min(completed, totalWork) + "/" + totalWork + ")" : ""));
            }
            currentTask = null;
        }

        @Override
        public boolean isCancelled() {
            return sink != null && sink.cancelled();
        }

        @Override
        public void showDuration(boolean enabled) {
            // JGit 7 API hook; DeployForge reports its own step durations.
        }

        private void emit(String message) {
            if (sink != null) {
                sink.accept(LogSink.LogLine.out(message));
            }
        }
    }
}
