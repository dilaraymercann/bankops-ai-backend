package com.bankops.application.auth;

import com.bankops.application.ResourceNotFoundException;
import com.bankops.application.repository.UserRepository;
import com.bankops.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    /** Kullanıcı bulunamadığında da BCrypt karşılaştırması yapılır; yanıt süresinden kullanıcı varlığı anlaşılmasın. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        TokenIssuer.IssuedToken token = tokenIssuer.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return userRepository.findById(userId)
                .map(UserSummary::from)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
    }
}
