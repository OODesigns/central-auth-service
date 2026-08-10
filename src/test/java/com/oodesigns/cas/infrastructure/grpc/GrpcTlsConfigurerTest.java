package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.infrastructure.adapter.KeySupplier;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcTlsConfigurerTest {

    /** 38-char password — satisfies {@code KeyPassword}'s 32-char minimum. */
    private static final String TEST_PASSWORD = "test-keystore-password-for-unit-tests";
    private static final String KEYSTORE_PASSWORD_KEY   = "KEYSTORE_PASSWORD";
    private static final String TRUSTSTORE_PASSWORD_KEY = "TRUSTSTORE_PASSWORD";

    @Mock private KeySupplier keySupplier;

    private GrpcTlsConfigurer configurer;

    private static String jksPath;
    private static String p12Path;
    private static String pfxPath;

    @BeforeAll
    static void resolveTestKeystorePaths() {
        jksPath = Objects.requireNonNull(
                GrpcTlsConfigurerTest.class.getClassLoader().getResource("tls/test-keystore.jks"),
                "test-keystore.jks not found on classpath"
        ).getPath();
        p12Path = Objects.requireNonNull(
                GrpcTlsConfigurerTest.class.getClassLoader().getResource("tls/test-keystore.p12"),
                "test-keystore.p12 not found on classpath"
        ).getPath();
        pfxPath = Objects.requireNonNull(
                GrpcTlsConfigurerTest.class.getClassLoader().getResource("tls/test-keystore.pfx"),
                "test-keystore.pfx not found on classpath"
        ).getPath();
    }

    @BeforeEach
    void setUp() {
        configurer = new GrpcTlsConfigurer(keySupplier);
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    @Test
    void constructor_ThrowsNPE_WhenKeySupplierIsNull() {
        assertThrows(NullPointerException.class, () -> new GrpcTlsConfigurer(null));
    }

    // =========================================================================
    // TLS disabled (no keystore path)
    // =========================================================================

    @Test
    void buildServerSslContext_ReturnsEmpty_WhenKeystorePathIsNull() {
        assertTrue(configurer.buildServerSslContext(null, null).isEmpty());
        verify(keySupplier, never()).getPassword(any());
    }

    @Test
    void buildServerSslContext_ReturnsEmpty_WhenKeystorePathIsBlank() {
        assertTrue(configurer.buildServerSslContext("   ", null).isEmpty());
        verify(keySupplier, never()).getPassword(any());
    }

    // =========================================================================
    // Keystore password unavailable
    // =========================================================================

    @Test
    void buildServerSslContext_ReturnsEmpty_WhenKeystorePasswordNotAvailable() {
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY)).thenReturn(Optional.empty());
        assertTrue(configurer.buildServerSslContext(jksPath, null).isEmpty());
    }

    // =========================================================================
    // Server-only TLS (no mTLS)
    // =========================================================================

    @Test
    void buildServerSslContext_ReturnsSslContext_ForValidJksKeystore() {
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        final Optional<SslContext> result = configurer.buildServerSslContext(jksPath, null);
        assertTrue(result.isPresent());
    }

    @Test
    void buildServerSslContext_ReturnsSslContext_ForValidPkcs12Keystore() {
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        final Optional<SslContext> result = configurer.buildServerSslContext(p12Path, null);
        assertTrue(result.isPresent());
    }

    @Test
    void buildServerSslContext_ReturnsSslContext_ForValidPfxKeystore() {
        // Exercises the .pfx extension branch in loadKeyStore (same PKCS12 format as .p12)
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        final Optional<SslContext> result = configurer.buildServerSslContext(pfxPath, null);
        assertTrue(result.isPresent());
    }

    @Test
    void buildServerSslContext_ReturnsEmpty_WhenKeystoreFileNotFound() {
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        assertTrue(configurer.buildServerSslContext("nonexistent-keystore.jks", null).isEmpty());
    }

    @Test
    void buildServerSslContext_ReturnsSslContext_WhenTruststorePathIsBlank() {
        // Blank truststore → server-only TLS, no mTLS
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        final Optional<SslContext> result = configurer.buildServerSslContext(jksPath, "  ");
        assertTrue(result.isPresent());
    }

    // =========================================================================
    // mTLS — truststore password unavailable
    // =========================================================================

    @Test
    void buildServerSslContext_ReturnsSslContext_WhenTruststorePasswordMissing() {
        // Truststore path is set but TRUSTSTORE_PASSWORD absent → mTLS skipped, server TLS OK
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        when(keySupplier.getPassword(TRUSTSTORE_PASSWORD_KEY))
                .thenReturn(Optional.empty());
        final Optional<SslContext> result = configurer.buildServerSslContext(jksPath, jksPath);
        assertTrue(result.isPresent());
    }

    // =========================================================================
    // mTLS — truststore fully configured
    // =========================================================================

    @Test
    void buildServerSslContext_ReturnsMtlsContext_WhenTruststoreIsValid() {
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        when(keySupplier.getPassword(TRUSTSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        final Optional<SslContext> result = configurer.buildServerSslContext(jksPath, jksPath);
        assertTrue(result.isPresent());
    }

    // =========================================================================
    // Exception path — truststore file not found (propagates to outer catch)
    // =========================================================================

    @Test
    void buildServerSslContext_ReturnsEmpty_WhenTruststoreFileNotFound() {
        // Valid keystore but nonexistent truststore → applyTruststore throws →
        // outer catch returns empty
        when(keySupplier.getPassword(KEYSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        when(keySupplier.getPassword(TRUSTSTORE_PASSWORD_KEY))
                .thenReturn(Optional.of(KeyPassword.of(TEST_PASSWORD)));
        final Optional<SslContext> result =
                configurer.buildServerSslContext(jksPath, "nonexistent-truststore.jks");
        assertTrue(result.isEmpty());
    }
}

