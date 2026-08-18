package io.campuscore.thesis.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("thesisPermissions")
public class ThesisPermissionEvaluator {

    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        try {
            return AccessContext.from(authentication).hasPermission(permission);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
