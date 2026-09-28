package com.xebia.ace.auth.login.repository;

import com.xebia.ace.auth.login.domain.AdministratorUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdministratorUserRepository extends JpaRepository<AdministratorUser, UUID> {

    @Query("""
            select u from AdministratorUser u
            where lower(u.username) = lower(:identifier) or lower(u.email) = lower(:identifier)
            """)
    List<AdministratorUser> findAllByLoginIdentifier(@Param("identifier") String identifier);

    /**
     * Resolves a login identifier as either a username or an email, case-insensitively.
     * A username match takes precedence if the identifier matches different users by username and email.
     */
    default Optional<AdministratorUser> findByUsernameOrEmailIgnoreCase(String identifier) {
        List<AdministratorUser> matches = findAllByLoginIdentifier(identifier);
        return matches.stream()
                .filter(user -> user.getUsername().equalsIgnoreCase(identifier))
                .findFirst()
                .or(() -> matches.stream().findFirst());
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AdministratorUser u
            set u.failedLoginCount = u.failedLoginCount + 1, u.updatedAt = CURRENT_TIMESTAMP
            where u.id = :userId
            """)
    void incrementFailedLoginCount(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AdministratorUser u
            set u.failedLoginCount = 0, u.lastLoginAt = :loginTime, u.updatedAt = CURRENT_TIMESTAMP
            where u.id = :userId
            """)
    void resetFailedLoginCountAndUpdateLastLogin(@Param("userId") UUID userId, @Param("loginTime") Instant loginTime);
}
