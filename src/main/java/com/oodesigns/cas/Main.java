package com.oodesigns.cas;

import com.oodesigns.cas.application.command.DisableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpCommandHandler;
import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.application.command.SetupTotpCommandHandler;
import com.oodesigns.cas.application.command.VerifyTotpCommandHandler;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.infrastructure.adapter.BcryptPasswordVerifier;
import com.oodesigns.cas.infrastructure.adapter.EnvironmentKeySupplier;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpSetupProvider;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpStatusReader;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpVerifier;
import com.oodesigns.cas.infrastructure.adapter.JooqUserCredentialByIdReader;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenSigner;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenVerifier;
import com.oodesigns.cas.infrastructure.adapter.LoginRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.SystemClock;
import com.oodesigns.cas.infrastructure.adapter.UserCredentialReader;
import com.oodesigns.cas.infrastructure.adapter.UserRepository;
import com.oodesigns.cas.infrastructure.config.DatabaseConfig;
import com.oodesigns.cas.infrastructure.config.DatabaseContextFactory;
import com.oodesigns.cas.infrastructure.grpc.AuthGrpcService;
import com.oodesigns.cas.infrastructure.grpc.GrpcTlsConfigurer;
import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;
import com.oodesigns.cas.util.properties.PropertiesReaderFactoryProvider;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import org.jooq.DSLContext;

import java.util.logging.Level;
import java.util.logging.Logger;

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
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String JWT_KEY_ID = "JWT_SECRET";
    private static final String TOTP_ENCRYPTION_KEY_ID = "KEYSTORE_PASSWORD";

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
        final EnvironmentKeySupplier keySupplier = new EnvironmentKeySupplier();
        final SystemClock clock = new SystemClock();
        final JwtTokenSigner tokenSigner = new JwtTokenSigner(keySupplier, JWT_KEY_ID);
        final JwtTokenVerifier tokenVerifier = new JwtTokenVerifier(keySupplier, JWT_KEY_ID);
        final BcryptPasswordVerifier passwordVerifier = new BcryptPasswordVerifier();
        final LoginRateLimiter rateLimiter = new LoginRateLimiter();
        final UserCredentialReader credentialReader = new UserCredentialReader(dsl);
        final UserRepository userRepository = new UserRepository(dsl);
        final JooqTotpStatusReader totpStatusReader = new JooqTotpStatusReader(dsl);
        final JooqTotpVerifier totpVerifier =
                new JooqTotpVerifier(dsl, clock, keySupplier, TOTP_ENCRYPTION_KEY_ID);
        final JooqTotpSetupProvider totpSetupProvider =
                new JooqTotpSetupProvider(dsl, keySupplier, TOTP_ENCRYPTION_KEY_ID);
        final JooqUserCredentialByIdReader credentialByIdReader =
                new JooqUserCredentialByIdReader(dsl);

        // --- Domain services ---
        final AuthenticationService authService = new AuthenticationService(passwordVerifier);
        final TokenService tokenService = new TokenService(clock, tokenSigner);

        // --- Command handlers ---
        final LoginCommandHandler loginHandler = new LoginCommandHandler(
                authService, tokenService, credentialReader, userRepository,
                totpStatusReader, rateLimiter);
        final SetupTotpCommandHandler setupTotpHandler =
                new SetupTotpCommandHandler(totpSetupProvider, issuerName);
        final EnableTotpCommandHandler enableTotpHandler =
                new EnableTotpCommandHandler(totpVerifier, totpSetupProvider);
        final VerifyTotpCommandHandler verifyTotpHandler =
                new VerifyTotpCommandHandler(tokenVerifier, totpVerifier, userRepository, tokenService);
        final DisableTotpCommandHandler disableTotpHandler =
                new DisableTotpCommandHandler(authService, credentialByIdReader, totpSetupProvider);

        // --- gRPC service ---
        final AuthGrpcService grpcService = new AuthGrpcService(
                loginHandler, setupTotpHandler, enableTotpHandler,
                verifyTotpHandler, disableTotpHandler);

        // --- TLS (optional) ---
        final String keystorePath = props.get("grpc.tls.keystore.path");
        final String truststorePath = props.get("grpc.tls.truststore.path");
        final GrpcTlsConfigurer tlsConfigurer = new GrpcTlsConfigurer(keySupplier);
        final java.util.Optional<SslContext> tlsContext =
                tlsConfigurer.buildServerSslContext(keystorePath, truststorePath);

        // --- Server ---
        final NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(grpcPort)
                .addService(grpcService);
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
            LOGGER.info("CAS gRPC server stopped.");
        }));

        server.awaitTermination();
    }
}

