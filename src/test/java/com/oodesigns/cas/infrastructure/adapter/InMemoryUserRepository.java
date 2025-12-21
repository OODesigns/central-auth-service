package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.repository.UserRepository;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test implementation of UserRepository.
 * Used for testing without database dependencies.
 */
public class InMemoryUserRepository implements UserRepository {
    private final Map<UserId, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        usersById.put(user.getUserId(), user);
        usersByUsername.put(user.getUsername().asString(), user);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        return Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        return Optional.ofNullable(usersByUsername.get(username.asString()));
    }

    @Override
    public boolean existsByUsername(Username username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        return usersByUsername.containsKey(username.asString());
    }

    public void clear() {
        usersById.clear();
        usersByUsername.clear();
    }

    public int size() {
        return usersById.size();
    }
}
