package io.flagship.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are always stored in lower case. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
