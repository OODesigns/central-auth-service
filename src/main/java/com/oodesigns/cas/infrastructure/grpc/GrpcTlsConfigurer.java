package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.infrastructure.adapter.KeySupplier;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Builds a Netty {@link SslContext} for the gRPC server from JKS / PKCS12 keystores.
 * <p>
 * Behaviour:
 * <ul>
 *   <li>If {@code keystorePath} is blank / null, plaintext mode is available only
 *       when the caller explicitly opts in.</li>
 *   <li>If TLS is configured but its key material cannot be loaded, startup fails.</li>
 *   <li>If a non-blank {@code truststorePath} is also provided and
 *       {@code TRUSTSTORE_PASSWORD} is available, mutual TLS is configured
 *       (client certificates required).</li>
 *   <li>Any I/O or SSL exception during configured setup fails closed.</li>
 * </ul>
 * <p>
 * Keystore format is inferred from the file extension:
 * {@code .p12} / {@code .pfx} → PKCS12; every other extension → JKS.
 * <p>
 * Secrets are sourced exclusively via {@link KeySupplier} using the environment
 * variable names {@code KEYSTORE_PASSWORD} and {@code TRUSTSTORE_PASSWORD}.
 * Char arrays are zeroed immediately after use.
 */
public final class GrpcTlsConfigurer {

    private static final Logger LOGGER = Logger.getLogger(GrpcTlsConfigurer.class.getName());
    private static final String KEYSTORE_PASSWORD_KEY   = "KEYSTORE_PASSWORD";
    private static final String TRUSTSTORE_PASSWORD_KEY = "TRUSTSTORE_PASSWORD";

    private final KeySupplier keySupplier;

    /**
     * @param keySupplier supplies keystore / truststore passwords from the environment
     */
    public GrpcTlsConfigurer(final KeySupplier keySupplier) {
        this.keySupplier = Objects.requireNonNull(keySupplier, "KeySupplier cannot be null");
    }

    /**
     * Build a server-side {@link SslContext}.
     *
     * @param keystorePath   path to the server keystore (JKS or PKCS12);
     *                       blank / null → plaintext mode
     * @param truststorePath optional path to a truststore for mTLS;
     *                       blank / null → no client authentication
    * @param allowPlaintext whether plaintext transport has been explicitly permitted
    * @return configured {@link SslContext}, or empty only when plaintext is explicitly allowed
    * @throws IllegalStateException if TLS is configured but cannot be initialized
     */
    public Optional<SslContext> buildServerSslContext(
            final String keystorePath,
            final String truststorePath,
            final boolean allowPlaintext) {
        if (keystorePath == null || keystorePath.isBlank()) {
            if (!allowPlaintext) {
                throw new IllegalStateException(
                        "KEYSTORE_PATH is required unless ALLOW_PLAINTEXT is explicitly true");
            }
            LOGGER.info("No keystore path configured; gRPC TLS disabled (plaintext mode)");
            return Optional.empty();
        }
        final KeyPassword password = keySupplier.getPassword(KEYSTORE_PASSWORD_KEY)
            .orElseThrow(() -> new IllegalStateException(
                "TLS is configured but KEYSTORE_PASSWORD is unavailable"));
        return Optional.of(loadSslContext(keystorePath, truststorePath, password));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SslContext loadSslContext(
            final String keystorePath,
            final String truststorePath,
            final KeyPassword keystorePassword) {
        try (keystorePassword) {
            final char[] pw = keystorePassword.chars();
            try {
                final KeyStore ks = loadKeyStore(keystorePath, pw);
                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, pw);
                final SslContextBuilder builder =
                        GrpcSslContexts.configure(SslContextBuilder.forServer(kmf));
                applyTruststore(builder, truststorePath);
                return builder.build();
            } finally {
                Arrays.fill(pw, '\0');
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to initialize configured gRPC TLS", e);
        }
    }

    /**
     * Optionally configures mTLS by loading a truststore and requiring client certs.
    * Throws if the truststore path is set but its password or file cannot be loaded.
     */
    private void applyTruststore(
            final SslContextBuilder builder,
            final String truststorePath) throws Exception {
        if (truststorePath == null || truststorePath.isBlank()) {
            return;
        }
        final KeyPassword truststorePassword = keySupplier.getPassword(TRUSTSTORE_PASSWORD_KEY)
                .orElseThrow(() -> new IllegalStateException(
                        "mTLS is configured but TRUSTSTORE_PASSWORD is unavailable"));
        try (truststorePassword) {
            final char[] pw = truststorePassword.chars();
            try {
                final KeyStore ts = loadKeyStore(truststorePath, pw);
                final TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
                builder.trustManager(tmf);
                builder.clientAuth(ClientAuth.REQUIRE);
                LOGGER.info(() -> "mTLS enabled with truststore: " + truststorePath);
            } finally {
                Arrays.fill(pw, '\0');
            }
        }
    }

    /**
     * Loads a keystore from the given path, auto-detecting format from the extension.
     */
    private KeyStore loadKeyStore(final String path, final char[] password) throws Exception {
        final String lower = path.toLowerCase();
        final String type = (lower.endsWith(".p12") || lower.endsWith(".pfx")) ? "PKCS12" : "JKS";
        final KeyStore ks = KeyStore.getInstance(type);
        try (final InputStream in = new FileInputStream(path)) {
            ks.load(in, password);
        }
        return ks;
    }
}

