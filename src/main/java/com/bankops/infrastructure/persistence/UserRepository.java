package com.bankops.infrastructure.persistence;

import com.bankops.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
