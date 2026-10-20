package com.sibang.hankki.auth.config;

import com.sibang.hankki.auth.ActiveSessionFilter;
import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<OwnerSessionUserDetailsService> userDetailsService)
            throws Exception {
        http
                .addFilterAfter(new ActiveSessionFilter(userDetailsService), SecurityContextHolderFilter.class)
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/reservations",
                        "/api/customer/reservations/*/cancel",
                        "/api/customer/reservations/*/check-in-token",
                        "/api/customer/reservations/*/change-requests",
                        "/api/customer/reservations/*/proposals/*/accept",
                        "/api/customer/reservations/*/proposals/*/decline",
                        "/api/customer/reservations/*/proposals/*/request-another-time"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/csrf", "/api/auth/login", "/api/auth/register", "/api/locations/**")
                        .permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/password").authenticated()
                        .requestMatchers("/api/customer/favorites", "/api/customer/favorites/**")
                        .hasRole("CUSTOMER")
                        .requestMatchers("/api/customer/account/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/customer/profile").hasRole("CUSTOMER")
                        .requestMatchers("/api/owner/settings/**").hasRole("OWNER")
                        .requestMatchers("/api/owner/**").hasAnyRole("OWNER", "STAFF")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("userid")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
