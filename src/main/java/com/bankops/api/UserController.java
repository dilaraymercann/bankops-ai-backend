package com.bankops.api;

import com.bankops.application.auth.UserQueryService;
import com.bankops.application.auth.UserSummary;
import com.bankops.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Kayıt atama listesi için kullanıcılar (yalnızca TeamLead). */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
class UserController {

    private final UserQueryService userQueryService;

    @GetMapping
    List<UserSummary> byRole(@RequestParam(defaultValue = "Specialist") Role role) {
        return userQueryService.byRole(role);
    }
}
