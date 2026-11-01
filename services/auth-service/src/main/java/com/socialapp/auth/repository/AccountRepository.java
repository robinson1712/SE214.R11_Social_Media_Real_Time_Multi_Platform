package com.socialapp.auth.repository;

import com.socialapp.auth.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("select count(a) from Account a join a.roles accountRole where accountRole = :role")
    long countByRole(@Param("role") String role);
}
