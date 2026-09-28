package com.xebia.ace.auth.login.repository;

import com.xebia.ace.auth.login.domain.LoginAuditRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuthAuditLogRepository extends JpaRepository<LoginAuditRecord, Long> {

    @Query("select r from LoginAuditRecord r where r.usernameOrEmail = :identifier order by r.id asc")
    List<LoginAuditRecord> findAllByLoginIdentifier(@Param("identifier") String identifier);
}
