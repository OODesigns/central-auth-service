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
 * Includes a save() method for test fixture setup (not part of the ports).
 */
public class InMemoryUserRepository implements Ports.UserCredentialReader, Ports.UserRepository {
    private final Map<UserId, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();

    /**
     * Test fixture helper to populate the repository.
     * Not part of the port contracts.
     */
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        usersById.put(user.userId(), user);
        usersByUsername.put(user.username().asString(), user);
    }

    @Override
    public Optional<UserCredential> findCredentialsByUsername(Username username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        return Optional.ofNullable(usersByUsername.get(username.asString()))
            .map(UserCredential::from);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return Optional.ofNullable(usersById.get(userId));
    }

    public void clear() {
        usersById.clear();
        usersByUsername.clear();
    }

    public int size() {
        return usersById.size();
    }
}
