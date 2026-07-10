package com.korit.feelioapi.domain.auth.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final String name;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(Long userId, String name, Map<String, Object> attributes) {
        this.userId = userId;
        this.name = name;
        this.attributes = attributes;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return name;
    }
}
