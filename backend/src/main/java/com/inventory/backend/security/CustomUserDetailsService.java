package com.inventory.backend.security;

import com.inventory.backend.model.User;
import com.inventory.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Spring Security calls this with the value the user typed in the "username" field.
     * We treat it as email first, then fall back to username, so the login form works
     * with either an email address or a short username.
     *
     * <p>The transaction ensures that the LAZY {@code grantedPermissions} and
     * {@code revokedPermissions} collections are initialized within the same
     * Hibernate session before the session closes, so that
     * {@link CustomUserDetails#getAuthorities()} (which calls
     * {@code user.getEffectivePermissions()}) can access them safely even after
     * the session has ended.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found for identifier: " + identifier));
        // Force-initialize the LAZY permission override collections so they are
        // accessible outside the transaction (e.g. in the security filter chain).
        user.getGrantedPermissions().size();
        user.getRevokedPermissions().size();
        return new CustomUserDetails(user);
    }
}
