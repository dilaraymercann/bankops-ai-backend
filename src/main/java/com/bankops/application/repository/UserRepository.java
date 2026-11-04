package com.bankops.application.repository;

import com.bankops.domain.Role;
import com.bankops.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findFirstByRole(Role role);

    List<User> findByRoleOrderByFullName(Role role);
}
