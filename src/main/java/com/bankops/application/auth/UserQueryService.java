package com.bankops.application.auth;

import com.bankops.application.repository.UserRepository;
import com.bankops.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    /** Atama listesi için belirli roldeki kullanıcılar (ada göre sıralı). */
    @Transactional(readOnly = true)
    public List<UserSummary> byRole(Role role) {
        return userRepository.findByRoleOrderByFullName(role).stream().map(UserSummary::from).toList();
    }
}
