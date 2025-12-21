package com.oodesigns.cas.domain.repository;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Optional;

/**
 * Port interface for user persistence.
 * Implementations handle DB/cache details.
 */
public interface UserRepository {
    void save(final User user);
    Optional<User> findById(final UserId userId);
    Optional<User> findByUsername(final Username username);
    boolean existsByUsername(final Username username);
}
