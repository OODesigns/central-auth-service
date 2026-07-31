package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.oodesigns.cas.util.file.FileLoaderProviderFactory;
import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatabaseContextFactory.
 * Tests DSLContext creation from DatabaseConfig.
 */
class DatabaseContextFactoryTest {
    
    @BeforeEach
    void setUp() {
        System.setProperty("DB_HOST", "localhost");
        System.setProperty("DB_PORT", "5432");
        System.setProperty("DB_USER", "app_user");
        System.setProperty("APP_PASSWORD", "Test@Password123");
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty("DB_HOST");
        System.clearProperty("DB_PORT");
        System.clearProperty("DB_NAME");
        System.clearProperty("DB_USER");
        System.clearProperty("DB_PASSWORD");
    }
    
    private DatabaseConfig createConfig() {
        if (System.getProperty("DB_USER", "").isBlank()) {
            System.setProperty("DB_USER", "app_user");
        }
        return new DatabaseConfig(
            new PropertiesReader(
                "application.properties",
                new EnvironmentVariableTransformer(),
                FileLoaderProviderFactory.defaultProvider()
            )
        );
    }
    
    @Test
    void testFactoryCreatesValidDslContext() throws SQLException {
        final DatabaseConfig config = createConfig();
        final DataSource mockDataSource = mock(DataSource.class);
        final Connection mockConnection = mock(Connection.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS)).thenReturn(true);
        
        final DSLContext dslContext = DatabaseContextFactory.create(config, c -> mockDataSource);
        
        assertNotNull(dslContext);
        verify(mockConnection).isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS);
        verify(mockConnection).close();
    }
    
    @Test
    void testFactoryThrowsOnInvalidConnection() throws SQLException {
        final DatabaseConfig config = createConfig();
        final DataSource mockDataSource = mock(DataSource.class);
        final Connection mockConnection = mock(Connection.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS)).thenReturn(false);
        
        final DatabaseConnectionException exception = assertThrows(
            DatabaseConnectionException.class,
            () -> DatabaseContextFactory.create(config, c -> mockDataSource)
        );
        
        assertEquals("Database connection validation failed", exception.getMessage());
    }
    
    @Test
    void testFactoryThrowsOnSQLException() throws SQLException {
        final DatabaseConfig config = createConfig();
        final DataSource mockDataSource = mock(DataSource.class);
        final SQLException sqlException = new SQLException("Connection refused");
        
        when(mockDataSource.getConnection()).thenThrow(sqlException);
        
        final DatabaseConnectionException exception = assertThrows(
            DatabaseConnectionException.class,
            () -> DatabaseContextFactory.create(config, c -> mockDataSource)
        );
        
        assertEquals("Unable to connect to database", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }
    
    @Test
    void testFactoryThrowsOnInvalidConfig() {
        System.setProperty("DB_PORT", "99999");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }
    
    @Test
    void testFactoryThrowsOnNullConfig() {
        assertThrows(NullPointerException.class, () -> DatabaseContextFactory.create(null));
    }
    
    @Test
    void testFactoryThrowsOnNullDataSourceFactory() {
        final DatabaseConfig config = createConfig();
        
        assertThrows(NullPointerException.class, 
            () -> DatabaseContextFactory.create(config, null));
    }
    
    @Test
    void testCreateDataSourceConfiguresCorrectly() {
        final DatabaseConfig config = createConfig();
        
        final DataSource dataSource = DatabaseContextFactory.createDataSource(config);
        
        assertNotNull(dataSource);
    }
    
    @Test
    void testPublicCreateMethodWithMockedDataSource() throws SQLException {
        final DatabaseConfig config = createConfig();
        final DataSource mockDataSource = mock(DataSource.class);
        final Connection mockConnection = mock(Connection.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS)).thenReturn(true);
        
        // Use the package-private create method to inject the mock DataSource factory
        // This avoids using mockStatic which requires the inline mock-maker agent
        final DSLContext result = DatabaseContextFactory.create(config, c -> mockDataSource);

        assertNotNull(result);
    }

    @Test
    void testValidateConnectionWithValidConnectionSucceeds() throws SQLException {
        final DataSource mockDataSource = mock(DataSource.class);
        final Connection mockConnection = mock(Connection.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS)).thenReturn(true);
        
        // Should not throw
        DatabaseContextFactory.validateConnection(mockDataSource);
        
        verify(mockConnection).isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS);
        verify(mockConnection).close();
    }

    @Test
    void testValidateConnectionWithInvalidConnectionThrows() throws SQLException {
        final DataSource mockDataSource = mock(DataSource.class);
        final Connection mockConnection = mock(Connection.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS)).thenReturn(false);
        
        final DatabaseConnectionException exception = assertThrows(
            DatabaseConnectionException.class,
            () -> DatabaseContextFactory.validateConnection(mockDataSource)
        );
        
        assertEquals("Database connection validation failed", exception.getMessage());
    }

    @Test
    void testValidateConnectionWithSQLExceptionThrows() throws SQLException {
        final DataSource mockDataSource = mock(DataSource.class);
        final SQLException sqlException = new SQLException("Connection refused");
        
        when(mockDataSource.getConnection()).thenThrow(sqlException);
        
        final DatabaseConnectionException exception = assertThrows(
            DatabaseConnectionException.class,
            () -> DatabaseContextFactory.validateConnection(mockDataSource)
        );
        
        assertEquals("Unable to connect to database", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }

    @Test
    void testCreateDataSourceReturnsValidDataSource() {
        final DatabaseConfig config = createConfig();
        
        final DataSource dataSource = DatabaseContextFactory.createDataSource(config);
        
        assertNotNull(dataSource);
        assertInstanceOf(DataSource.class, dataSource);
    }

    @Test
    void testConnectionTimeoutConstantsAreSet() {
        assertEquals(30, DatabaseContextFactory.CONNECTION_TIMEOUT_SECONDS);
        assertEquals(5, DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS);
    }

    @Test
    void testFactoryMethodsWithValidConfigAndDataSource() throws SQLException {
        final DatabaseConfig config = createConfig();
        final DataSource mockDataSource = mock(DataSource.class);
        final Connection mockConnection = mock(Connection.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(DatabaseContextFactory.VALIDATION_TIMEOUT_SECONDS)).thenReturn(true);
        
        final DSLContext dslContext = DatabaseContextFactory.create(config, c -> mockDataSource);
        
        assertNotNull(dslContext);
    }
}
