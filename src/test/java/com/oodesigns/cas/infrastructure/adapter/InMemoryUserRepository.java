package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.UserCredential;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test implementation of UserCredentialReader and UserRepository.
 * Used for testing without database dependencies.
 * Note: Tests must set up both User (for post-auth) and UserCredential (for auth)
 * separately, as User no longer contains password hash.
 */
public class InMemoryUserRepository implements Ports.UserCredentialReader, Ports.UserRepository {
    private final Map<UserId, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, UserCredential> credentialsByUsername = new ConcurrentHashMap<>();

    /**
     * Test fixture helper to save a user (for authorization/profile data).
     * Not part of the port contracts.
     */
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        usersById.put(user.userId(), user);
        usersByUsername.put(user.username().asString(), user);
    }

    /**
     * Test fixture helper to save credentials separately (for authentication).
     * Not part of the port contracts.
     * 
     * @param credential the user credential with userId and password hash
     */
    public void saveCredential(UserCredential credential) {
        if (credential == null) {
            throw new IllegalArgumentException("UserCredential cannot be null");
        }
        // Store by userId string since we need to match credentials to users
        credentialsByUsername.put(credential.userId().toString(), credential);
    }

    @Override
    public Optional<UserCredential> findCredentialsByUsername(Username username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        // Find user by username to get userId, then look up credential
        return Optional.ofNullable(usersByUsername.get(username.asString()))
            .flatMap(user -> Optional.ofNullable(credentialsByUsername.get(user.userId().toString())));
    }

    @Override
    public Optional<User> findById(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return Optional.ofNullable(usersById.get(userId));
    }

    public int size() {
        return usersById.size();
    }
}
