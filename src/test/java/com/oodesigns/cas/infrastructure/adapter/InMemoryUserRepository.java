package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test implementation of UserRepositoryReader.
 * Used for testing without database dependencies.
 * Includes a save() method for test fixture setup (not part of the port).
 */
public class InMemoryUserRepository implements Ports.UserRepositoryReader {
    private final Map<UserId, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();

    /**
     * Test fixture helper to populate the repository.
     * Not part of the UserRepositoryReader port contract.
     */
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        usersById.put(user.userId(), user);
        usersByUsername.put(user.username().asString(), user);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        return Optional.ofNullable(usersByUsername.get(username.asString()));
    }

    public void clear() {
        usersById.clear();
        usersByUsername.clear();
    }

    public int size() {
        return usersById.size();
    }
}
