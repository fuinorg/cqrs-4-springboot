package org.fuin.cqrs4j.springboot.query.core;

import org.fuin.cqrs4j.core.QueryExecutionContext;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecurityContextQueryExecutionContextProvider}.
 */
public class SecurityContextQueryExecutionContextProviderTest {

    private static final TenantId TENANT = new TenantId("acme");

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testAuthenticatedCallerBecomesTheUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("subject-1", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_user"))));

        final QueryExecutionContext context = provider().current();

        assertThat(context.getUser().getUserId()).isEqualTo("subject-1");
        assertThat(context.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    public void testAuthoritiesBecomeRolesVerbatim() {
        // Verbatim, prefix included. A bypass role configured without the ROLE_ prefix would silently never
        // match, which is the classic way this fails.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("subject-1", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_org-admin"))));

        assertThat(provider().currentUserRoles()).containsExactly(new SimpleRole("ROLE_org-admin"));
    }

    @Test
    public void testUnauthenticatedRequestYieldsTheFallbackUserAndNoRoles() {
        assertThat(provider().current().getUser().getUserId()).isEqualTo("anonymous");
        assertThat(provider().currentUserRoles()).isEmpty();
    }

    private static SecurityContextQueryExecutionContextProvider provider() {
        return new SecurityContextQueryExecutionContextProvider(TENANT, "anonymous");
    }

}
