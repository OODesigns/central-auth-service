package com.oodesigns.cas.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JooqUserRepository")
class JooqUserRepositoryTest {

    private DSLContext dslContext;
    private JooqUserRepository userRepository;

    @BeforeEach
    void setUp() {
        dslContext = mock(DSLContext.class);
        userRepository = new JooqUserRepository(dslContext);
    }

    @Test
    @DisplayName("findById returns User with single permission when record exists")
    void testFindByIdWithSinglePermission() {
        final UUID userId = UUID.randomUUID();
        final String username = "testuser";
        final String[] permissions = {"view_users"};

        final Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(userId);
        when(jooqRecord.get("username", String.class)).thenReturn(username);
        when(jooqRecord.get("permissions", String[].class)).thenReturn(permissions);

        when(dslContext.fetchOptional(
                "SELECT * FROM auth.get_user(?)", userId
        )).thenReturn(Optional.of(jooqRecord));

        final Optional<User> result = userRepository.findById(UserId.of(userId));

        assertTrue(result.isPresent());
        final User user = result.get();
        assertEquals(userId, user.userId().value());
        assertEquals(username, user.username().value());
        assertEquals(1, user.permissions().size());
        assertTrue(user.permissions().contains(Permission.of("view_users")));
    }

    @Test
    @DisplayName("findById returns User with multiple permissions when record exists")
    void testFindByIdWithMultiplePermissions() {
        final UUID userId = UUID.randomUUID();
        final String username = "admin";
        final String[] permissions = {"view_users", "edit_profile", "delete_accounts"};

        final Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(userId);
        when(jooqRecord.get("username", String.class)).thenReturn(username);
        when(jooqRecord.get("permissions", String[].class)).thenReturn(permissions);

        when(dslContext.fetchOptional(
                "SELECT * FROM auth.get_user(?)", userId
        )).thenReturn(Optional.of(jooqRecord));

        final Optional<User> result = userRepository.findById(UserId.of(userId));

        assertTrue(result.isPresent());
        final User user = result.get();
        assertEquals(userId, user.userId().value());
        assertEquals(username, user.username().value());
        assertEquals(3, user.permissions().size());
        assertTrue(user.permissions().contains(Permission.of("view_users")));
        assertTrue(user.permissions().contains(Permission.of("edit_profile")));
        assertTrue(user.permissions().contains(Permission.of("delete_accounts")));
    }

    @Test
    @DisplayName("findById returns User with empty permissions when permissions array is null")
    void testFindByIdWithNullPermissions() {
        final UUID userId = UUID.randomUUID();
        final String username = "user_no_perms";

        final Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(userId);
        when(jooqRecord.get("username", String.class)).thenReturn(username);
        when(jooqRecord.get("permissions", String[].class)).thenReturn(null);

        when(dslContext.fetchOptional(
                "SELECT * FROM auth.get_user(?)", userId
        )).thenReturn(Optional.of(jooqRecord));

        final Optional<User> result = userRepository.findById(UserId.of(userId));

        assertTrue(result.isPresent());
        final User user = result.get();
        assertEquals(userId, user.userId().value());
        assertEquals(username, user.username().value());
        assertTrue(user.permissions().isEmpty());
    }

    @Test
    @DisplayName("findById returns User with empty permissions when permissions array is empty")
    void testFindByIdWithEmptyPermissions() {
        final UUID userId = UUID.randomUUID();
        final String username = "user_no_perms";
        final String[] permissions = {};

        final Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(userId);
        when(jooqRecord.get("username", String.class)).thenReturn(username);
        when(jooqRecord.get("permissions", String[].class)).thenReturn(permissions);

        when(dslContext.fetchOptional(
                "SELECT * FROM auth.get_user(?)", userId
        )).thenReturn(Optional.of(jooqRecord));

        final Optional<User> result = userRepository.findById(UserId.of(userId));

        assertTrue(result.isPresent());
        final User user = result.get();
        assertEquals(userId, user.userId().value());
        assertEquals(username, user.username().value());
        assertTrue(user.permissions().isEmpty());
    }

    @Test
    @DisplayName("findById returns empty Optional when no record found")
    void testFindByIdNotFound() {
        final UUID userId = UUID.randomUUID();

        when(dslContext.fetchOptional(
                "SELECT * FROM auth.get_user(?)", userId
        )).thenReturn(Optional.empty());

        final Optional<User> result = userRepository.findById(UserId.of(userId));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findById returns empty Optional when userId is null")
    void testFindByIdWithNullUserId() {
        final Optional<User> result = userRepository.findById(null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("constructor throws NullPointerException when DSLContext is null")
    void testConstructorWithNullDslContext() {
        try {
            new JooqUserRepository(null);
            assertTrue(false, "Expected NullPointerException");
        } catch (final NullPointerException e) {
            assertEquals("DSLContext cannot be null", e.getMessage());
        }
    }

    @Test
    @DisplayName("findById returns immutable permission set")
    void testFindByIdReturnsImmutablePermissions() {
        final UUID userId = UUID.randomUUID();
        final String username = "testuser";
        final String[] permissions = {"view_users"};

        final Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(userId);
        when(jooqRecord.get("username", String.class)).thenReturn(username);
        when(jooqRecord.get("permissions", String[].class)).thenReturn(permissions);

        when(dslContext.fetchOptional(
                "SELECT * FROM auth.get_user(?)", userId
        )).thenReturn(Optional.of(jooqRecord));

        final Optional<User> result = userRepository.findById(UserId.of(userId));

        assertTrue(result.isPresent());
        final User user = result.get();
        final Set<Permission> perms = user.permissions();

        // Verify immutability by attempting to modify
        try {
            perms.add(Permission.of("edit_profile"));
            assertTrue(false, "Expected UnsupportedOperationException");
        } catch (final UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }
}
