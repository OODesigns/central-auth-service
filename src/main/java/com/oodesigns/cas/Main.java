package com.oodesigns.cas;

import com.oodesigns.cas.application.command.DisableTotpCommandHandler;
import com.oodesigns.cas.application.command.AdminDisableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpCommandHandler;
import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.application.command.LogoutCommandHandler;
import com.oodesigns.cas.application.command.RefreshTokenCommandHandler;
import com.oodesigns.cas.application.command.SetupTotpCommandHandler;
import com.oodesigns.cas.application.command.VerifyTotpCommandHandler;
import com.oodesigns.cas.application.command.IssueRecoveryTokenCommandHandler;
import com.oodesigns.cas.application.command.CompleteRecoveryCommandHandler;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.infrastructure.adapter.BcryptPasswordVerifier;
import com.oodesigns.cas.infrastructure.adapter.DatabaseLoginRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.DatabaseTotpRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.EnvironmentKeySupplier;
import com.oodesigns.cas.infrastructure.adapter.FileKeySupplier;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpSetupProvider;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpStatusReader;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpVerifier;
import com.oodesigns.cas.infrastructure.adapter.JooqUserCredentialByIdReader;
import com.oodesigns.cas.infrastructure.adapter.JooqRefreshTokenStore;
import com.oodesigns.cas.infrastructure.adapter.JooqAccessTokenRevocationStore;
import com.oodesigns.cas.infrastructure.adapter.JooqTrustedClientRetriever;
import com.oodesigns.cas.infrastructure.adapter.JooqRecoveryTokenStore;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenSigner;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenVerifier;
import com.oodesigns.cas.infrastructure.adapter.KeySupplier;
import com.oodesigns.cas.infrastructure.adapter.LoginRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.SystemClock;
import com.oodesigns.cas.infrastructure.adapter.TotpRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.UserCredentialReader;
import com.oodesigns.cas.infrastructure.adapter.UserRepository;
import com.oodesigns.cas.infrastructure.config.DatabaseConfig;
import com.oodesigns.cas.infrastructure.config.DatabaseContextFactory;
import com.oodesigns.cas.infrastructure.grpc.AuthGrpcService;
import com.oodesigns.cas.infrastructure.grpc.GrpcTlsConfigurer;
import com.oodesigns.cas.infrastructure.grpc.GrpcAuthInterceptor;
import com.oodesigns.cas.infrastructure.grpc.GrpcMetricsInterceptor;
import com.oodesigns.cas.infrastructure.grpc.GrpcSecurityEventInterceptor;
import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;
import com.oodesigns.cas.util.properties.PropertiesReaderFactoryProvider;
import io.grpc.Server;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.jooq.DSLContext;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.stream.Stream;
import java.nio.file.Path;

/**
 * Application entry point.
 * <p>
 * Wires all infrastructure adapters, domain services, and command handlers together,
 * then starts the gRPC server. All configuration is sourced from {@code application.properties}
 * (with {@code ${ENV_VAR:default}} expansion) so no secrets are hardcoded.
 * <p>
 * TLS: Configured via {@code grpc.tls.keystore.path} / {@code grpc.tls.truststore.path}
 * properties and the {@code KEYSTORE_PASSWORD} / {@code TRUSTSTORE_PASSWORD} env vars.
 * When {@code grpc.tls.keystore.path} is blank the server starts in plaintext mode,
 * suitable for local development or when TLS is terminated by a reverse proxy / sidecar.
 */
@SuppressWarnings("null")
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
        private static final String TOTP_ENCRYPTION_KEY_ID = "TOTP_ENCRYPTION_KEY";

    private Main() { /* utility class */ }

    public static void main(final String[] args) throws Exception {
        final PropertiesReader props = PropertiesReaderFactoryProvider.create()
                .create(new EnvironmentVariableTransformer());

        final int grpcPort = Integer.parseInt(props.get("grpc.port"));
        final String issuerName = props.get("totp.issuer");

        // --- Database ---
        final DatabaseConfig dbConfig = new DatabaseConfig(props);
        final DSLContext dsl = DatabaseContextFactory.create(dbConfig);

        // --- Infrastructure adapters ---
                final KeySupplier keySupplier = switch (props.get("secrets.backend")) {
                        case "environment" -> new EnvironmentKeySupplier();
                        case "file" -> new FileKeySupplier(Path.of(props.get("secrets.directory")));
                        default -> throw new IllegalArgumentException("Unsupported secrets backend");
                };
        final SystemClock clock = new SystemClock();
        final JooqAccessTokenRevocationStore accessTokenRevocationStore = new JooqAccessTokenRevocationStore(dsl);
        final String activeJwtKeyId = props.get("jwt.active-key-id");
        final java.util.List<String> jwtVerificationKeyIds = Stream.concat(
                Stream.of(activeJwtKeyId),
                Arrays.stream(props.get("jwt.previous-key-ids").split(",")))
            .map(keyId -> keyId.trim())
            .filter(keyId -> !keyId.isEmpty())
            .distinct()
            .toList();
        final JwtTokenSigner tokenSigner = new JwtTokenSigner(keySupplier, activeJwtKeyId);
        final JwtTokenVerifier tokenVerifier =
                new JwtTokenVerifier(keySupplier, jwtVerificationKeyIds, accessTokenRevocationStore);
        final BcryptPasswordVerifier passwordVerifier = new BcryptPasswordVerifier();
                final Ports.RateLimiter rateLimiter = switch (props.get("rate-limit.backend")) {
                        case "database" -> new DatabaseLoginRateLimiter(dsl);
                        case "memory" -> new LoginRateLimiter();
                        default -> throw new IllegalArgumentException("Unsupported rate-limit backend");
                };
                final Ports.TotpRateLimiter totpRateLimiter = switch (props.get("totp-rate-limit.backend")) {
                        case "database" -> new DatabaseTotpRateLimiter(dsl);
                        case "memory" -> new TotpRateLimiter();
                        default -> throw new IllegalArgumentException("Unsupported TOTP rate-limit backend");
                };
        final UserCredentialReader credentialReader = new UserCredentialReader(dsl);
        final UserRepository userRepository = new UserRepository(dsl);
        final JooqTrustedClientRetriever trustedClientRetriever = new JooqTrustedClientRetriever(dsl);
        final JooqTotpStatusReader totpStatusReader = new JooqTotpStatusReader(dsl);
        final JooqTotpVerifier totpVerifier =
                new JooqTotpVerifier(dsl, clock, keySupplier, TOTP_ENCRYPTION_KEY_ID);
        final JooqTotpSetupProvider totpSetupProvider =
                new JooqTotpSetupProvider(dsl, keySupplier, TOTP_ENCRYPTION_KEY_ID);
        final JooqUserCredentialByIdReader credentialByIdReader =
                new JooqUserCredentialByIdReader(dsl);
        final JooqRefreshTokenStore refreshTokenStore = new JooqRefreshTokenStore(dsl);
        final JooqRecoveryTokenStore recoveryTokenStore = new JooqRecoveryTokenStore(dsl);

        // --- Domain services ---
        final AuthenticationService authService = new AuthenticationService(passwordVerifier);
        final TokenService tokenService = new TokenService(clock, tokenSigner);

        // --- Command handlers ---
        final LoginCommandHandler loginHandler = new LoginCommandHandler(
                authService, tokenService, credentialReader, userRepository,
                totpStatusReader, rateLimiter, refreshTokenStore);
        final SetupTotpCommandHandler setupTotpHandler =
                new SetupTotpCommandHandler(totpSetupProvider, issuerName);
        final EnableTotpCommandHandler enableTotpHandler =
                new EnableTotpCommandHandler(totpVerifier, totpSetupProvider);
        final VerifyTotpCommandHandler verifyTotpHandler =
                new VerifyTotpCommandHandler(tokenVerifier, totpVerifier, userRepository, tokenService, totpRateLimiter, refreshTokenStore);
        final DisableTotpCommandHandler disableTotpHandler =
                new DisableTotpCommandHandler(authService, credentialByIdReader, totpSetupProvider);
        final AdminDisableTotpCommandHandler adminDisableTotpHandler =
                new AdminDisableTotpCommandHandler(authService, credentialByIdReader, totpSetupProvider);
        final RefreshTokenCommandHandler refreshTokenHandler =
                new RefreshTokenCommandHandler(tokenVerifier, userRepository, tokenService, refreshTokenStore);
        final LogoutCommandHandler logoutHandler = new LogoutCommandHandler(tokenVerifier, accessTokenRevocationStore);
        final IssueRecoveryTokenCommandHandler issueRecoveryTokenHandler =
                new IssueRecoveryTokenCommandHandler(tokenService, recoveryTokenStore);
        final CompleteRecoveryCommandHandler completeRecoveryHandler =
                new CompleteRecoveryCommandHandler(tokenVerifier, passwordVerifier, recoveryTokenStore);

        // --- gRPC service ---
        final AuthGrpcService grpcService = new AuthGrpcService(
                loginHandler, setupTotpHandler, enableTotpHandler,
                verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler,
                refreshTokenHandler, logoutHandler, issueRecoveryTokenHandler, completeRecoveryHandler);

        // --- TLS (optional) ---
        final String keystorePath = props.get("grpc.tls.keystore.path");
        final String truststorePath = props.get("grpc.tls.truststore.path");
        final boolean allowPlaintext = Boolean.parseBoolean(props.get("grpc.tls.allow-plaintext"));
        final int maxInboundMessageBytes = Integer.parseInt(props.get("grpc.max-inbound-message-bytes"));
        final int maxInboundMetadataBytes = Integer.parseInt(props.get("grpc.max-inbound-metadata-bytes"));
        final long keepAliveTimeMinutes = Long.parseLong(props.get("grpc.keepalive-time-minutes"));
        final long keepAliveTimeoutSeconds = Long.parseLong(props.get("grpc.keepalive-timeout-seconds"));
        final long maxConnectionIdleMinutes = Long.parseLong(props.get("grpc.max-connection-idle-minutes"));
        final boolean requireMachineClient = Boolean.parseBoolean(props.get("grpc.tls.require-machine-client"));
        final boolean healthEnabled = Boolean.parseBoolean(props.get("grpc.health.enabled"));
        final boolean reflectionEnabled = Boolean.parseBoolean(props.get("grpc.reflection.enabled"));
        final int metricsPort = Integer.parseInt(props.get("otel.metrics.port"));
        final String environment = props.get("deployment.environment");
        final PrometheusHttpServer prometheusServer = PrometheusHttpServer.builder()
                .setPort(metricsPort)
                .setHost("0.0.0.0")
                .build();
        final SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(prometheusServer)
                .build();
        final GrpcTlsConfigurer tlsConfigurer = new GrpcTlsConfigurer(keySupplier);
        final java.util.Optional<SslContext> tlsContext =
                tlsConfigurer.buildServerSslContext(keystorePath, truststorePath, allowPlaintext);

        // --- Server ---
        final NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(grpcPort)
                .maxInboundMessageSize(maxInboundMessageBytes)
                .maxInboundMetadataSize(maxInboundMetadataBytes)
                .keepAliveTime(keepAliveTimeMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .keepAliveTimeout(keepAliveTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(false)
                .maxConnectionIdle(maxConnectionIdleMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .intercept(new GrpcMetricsInterceptor(meterProvider, environment))
                .intercept(new GrpcSecurityEventInterceptor(environment))
                .intercept(new GrpcAuthInterceptor(tokenVerifier, userRepository,
                        trustedClientRetriever, requireMachineClient))
                .addService(grpcService);
                final HealthStatusManager healthStatusManager = new HealthStatusManager();
                if (healthEnabled) {
                        healthStatusManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
                        serverBuilder.addService(healthStatusManager.getHealthService());
                }
                if (reflectionEnabled) {
                        serverBuilder.addService(ProtoReflectionService.newInstance());
                        serverBuilder.addService(ProtoReflectionServiceV1.newInstance());
                }
        tlsContext.ifPresent(serverBuilder::sslContext);
        final Server server = serverBuilder.build().start();

        if (tlsContext.isPresent()) {
            LOGGER.log(Level.INFO, "CAS gRPC server started with TLS on port {0}", grpcPort);
        } else {
            LOGGER.log(Level.INFO, "CAS gRPC server started (plaintext) on port {0}", grpcPort);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down CAS gRPC server...");
            server.shutdown();
            prometheusServer.close();
            meterProvider.close();
            LOGGER.info("CAS gRPC server stopped.");
        }));

        server.awaitTermination();
    }
}

