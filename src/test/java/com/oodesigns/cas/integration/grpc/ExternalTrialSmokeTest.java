package com.oodesigns.cas.integration.grpc;

import com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ConnectivityState;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Process-level smoke test for a running trial Compose stack. */
@Tag("external-smoke")
class ExternalTrialSmokeTest {
    private static final int USER_COUNT = 100;
    private static final String PASSWORD = "TrialSmokePassword123!";
    private static final String USER_PREFIX = "trial_smoke_";
    private static final String DEFAULT_ENV_FILE = ".trial.env";
    private static final String DEFAULT_DB_HOST = "127.0.0.1";
    private static final String DEFAULT_DB_PORT = "55432";
    private static final String DEFAULT_GRPC_HOST = "127.0.0.1";
    private static final String DEFAULT_GRPC_PORT = "50051";
    private static final String DEFAULT_PROMETHEUS_URL = "http://127.0.0.1:9090";

    private static Connection adminConnection;
    private static ManagedChannel channel;
    private static final List<UUID> createdUsers = new ArrayList<>();

    @BeforeAll
    static void startClient() throws Exception {
        final Map<String, String> environment = loadEnvironment();
        adminConnection = DriverManager.getConnection(
                "jdbc:postgresql://%s:%s/%s".formatted(
                        value(environment, "DB_HOST", DEFAULT_DB_HOST),
                        value(environment, "DB_PORT", value(environment, "POSTGRES_HOST_PORT", DEFAULT_DB_PORT)),
                        value(environment, "APP_DB", "auth_db")),
                value(environment, "POSTGRES_USER", "postgres"),
                value(environment, "POSTGRES_PASSWORD", "postgres"));
        channel = ManagedChannelBuilder.forAddress(
                        value(environment, "GRPC_HOST", DEFAULT_GRPC_HOST),
                        Integer.parseInt(value(environment, "GRPC_PORT", value(environment, "GRPC_HOST_PORT", DEFAULT_GRPC_PORT))))
                .usePlaintext()
                .build();
            awaitGrpcReady();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        if (adminConnection != null && !adminConnection.isClosed()) {
            deleteFixtureUsers();
            adminConnection.close();
        }
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void createsOneHundredUsersExercisesGrpcAndChecksMetrics() throws Exception {
        final UUID roleId = userRoleId();
        final String passwordHash = new BCryptPasswordEncoder().encode(PASSWORD);
        clearFixtureRateLimits();
        deleteFixtureUsers();
        insertUsers(roleId, passwordHash);

        final AuthServiceGrpc.AuthServiceBlockingStub stub = AuthServiceGrpc.newBlockingStub(channel);
        for (int index = 0; index < 3; index++) {
            final LoginResponse response = login(stub, username(index), PASSWORD);
            assertTrue(response.hasSuccess(), "Expected trial user login to succeed: " + username(index));
        }

        final String originalUsername = username(2);
        final String updatedUsername = originalUsername + "_updated";
        updateUsername(originalUsername, updatedUsername);
        assertTrue(login(stub, updatedUsername, PASSWORD).hasSuccess(), "Updated user should log in");

        final String deletedUsername = username(1);
        deleteUser(deletedUsername);
        assertEquals(0, countUser(deletedUsername), "Deleted user should be removed");
        final StatusRuntimeException deletedLogin = org.junit.jupiter.api.Assertions.assertThrows(
            StatusRuntimeException.class, () -> login(stub, deletedUsername, PASSWORD));
        assertEquals(Status.Code.UNAUTHENTICATED, deletedLogin.getStatus().getCode(),
            "Deleted user should no longer log in");

        assertEquals(USER_COUNT - 1, countFixtureUsers(), "One fixture user should have been deleted");
        assertTrue(prometheusContainsGrpcCalls(), "Prometheus should expose gRPC calls from this workload");
    }

    private static LoginResponse login(final AuthServiceGrpc.AuthServiceBlockingStub stub,
                                       final String username,
                                       final String password) {
        return stub.login(LoginRequest.newBuilder().setUsername(username).setPassword(password).build());
    }

    private static void insertUsers(final UUID roleId, final String passwordHash) throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "INSERT INTO private_schema.users (username, password_hash, role_id, password_reset_required_at) "
                        + "VALUES (?, ?, ?, NULL) RETURNING user_id")) {
            for (int index = 0; index < USER_COUNT; index++) {
                statement.setString(1, username(index));
                statement.setString(2, passwordHash);
                statement.setObject(3, roleId);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    createdUsers.add(rows.getObject(1, UUID.class));
                }
            }
        }
    }

    private static void deleteFixtureUsers() throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "DELETE FROM private_schema.users WHERE username LIKE ?")) {
            statement.setString(1, USER_PREFIX + "%");
            statement.executeUpdate();
        }
    }

    private static void clearFixtureRateLimits() throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "DELETE FROM private_schema.login_rate_limits")) {
            statement.executeUpdate();
        }
    }

    private static UUID userRoleId() throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "SELECT role_id FROM private_schema.roles WHERE name = 'user'")) {
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next(), "The user role must be seeded");
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private static void updateUsername(final String original, final String updated) throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "UPDATE private_schema.users SET username = ?, updated_at = now() WHERE username = ?")) {
            statement.setString(1, updated);
            statement.setString(2, original);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void deleteUser(final String username) throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "DELETE FROM private_schema.users WHERE username = ?")) {
            statement.setString(1, username);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static long countFixtureUsers() throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "SELECT count(*) FROM private_schema.users WHERE username LIKE ?")) {
            statement.setString(1, USER_PREFIX + "%");
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long countUser(final String username) throws SQLException {
        try (var statement = adminConnection.prepareStatement(
                "SELECT count(*) FROM private_schema.users WHERE username = ?")) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static boolean prometheusContainsGrpcCalls() throws IOException, InterruptedException {
        final String baseUrl = System.getProperty("smoke.prometheusUrl", DEFAULT_PROMETHEUS_URL);
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/query?query=grpc_server_call_count_total"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        final HttpClient client = HttpClient.newHttpClient();
        for (int attempt = 0; attempt < 30; attempt++) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("grpc_server_call_count_total")) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private static void awaitGrpcReady() throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            channel.getState(true);
            if (channel.getState(false) == ConnectivityState.READY) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("The trial gRPC service did not become ready");
    }

    private static String username(final int index) {
        return USER_PREFIX + "%03d".formatted(index + 1);
    }

    private static Map<String, String> loadEnvironment() throws IOException {
        final Path environmentFile = Path.of(System.getProperty("trialEnvFile", DEFAULT_ENV_FILE));
        if (!Files.exists(environmentFile)) {
            return Map.of();
        }
        return Files.readAllLines(environmentFile).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
                .map(line -> line.split("=", 2))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        values -> values[0], values -> values[1].replace("$$", "$"), (first, second) -> second));
    }

    private static String value(final Map<String, String> environment, final String key, final String fallback) {
        return environment.getOrDefault(key, System.getenv().getOrDefault(key, fallback));
    }
}
