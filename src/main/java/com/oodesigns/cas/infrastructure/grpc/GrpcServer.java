package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.infrastructure.adapter.BcryptPasswordVerifier;
import com.oodesigns.cas.infrastructure.adapter.Bucket4jRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.EnvironmentKeySupplier;
import com.oodesigns.cas.infrastructure.adapter.JooqUserCredentialReader;
import com.oodesigns.cas.infrastructure.adapter.JooqUserRepository;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenSigner;
import com.oodesigns.cas.infrastructure.adapter.SystemClock;
import com.oodesigns.cas.infrastructure.config.DatabaseConfig;
import com.oodesigns.cas.infrastructure.config.DatabaseContextFactory;
import com.oodesigns.cas.util.file.FileLoaderProvider;
import com.oodesigns.cas.util.properties.PropertiesReader;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.jooq.DSLContext;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GrpcServer {
    private static final Logger logger = Logger.getLogger(GrpcServer.class.getName());

    private Server server;

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 50051;

        // 1. Load Configuration
        PropertiesReader propertiesReader = new PropertiesReader("application.properties", s -> s, new FileLoaderProvider());
        DatabaseConfig dbConfig = new DatabaseConfig(propertiesReader);

        // 2. Setup Database
        DSLContext dslContext = DatabaseContextFactory.create(dbConfig);

        // 3. Setup Adapters
        var credentialReader = new JooqUserCredentialReader(dslContext);
        var userRepository = new JooqUserRepository(dslContext);
        var rateLimiter = new Bucket4jRateLimiter();
        var clock = new SystemClock();
        var passwordVerifier = new BcryptPasswordVerifier();
        var tokenSigner = new JwtTokenSigner(new EnvironmentKeySupplier(), "JWT_SECRET"); // Assuming JWT_SECRET env var

        // 4. Setup Domain Services
        var authService = new AuthenticationService(passwordVerifier);
        var tokenService = new TokenService(clock, tokenSigner);

        // 5. Setup Application Service
        var loginCommandHandler = new LoginCommandHandler(
            authService,
            tokenService,
            credentialReader,
            userRepository,
            rateLimiter
        );

        server = ServerBuilder.forPort(port)
            .addService(new AuthServiceImpl(loginCommandHandler))
            .build()
            .start();

        logger.info("Server started, listening on " + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    GrpcServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final GrpcServer server = new GrpcServer();
        server.start();
        server.blockUntilShutdown();
    }
}

