package com.korit.feelioapi.domain.auth.oauth;

import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * provider → SocialOAuthClient 매핑. 등록되지 않은 provider 는 INVALID_PROVIDER.
 * 각 client 빈을 주입받아 EnumMap 으로 색인한다.
 */
@Component
public class SocialOAuthClientResolver {

    private final Map<SocialProvider, SocialOAuthClient> clients = new EnumMap<>(SocialProvider.class);

    public SocialOAuthClientResolver(List<SocialOAuthClient> registeredClients) {
        for (SocialOAuthClient client : registeredClients) {
            clients.put(client.provider(), client);
        }
    }

    public SocialOAuthClient resolve(SocialProvider provider) {
        SocialOAuthClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }
        return client;
    }
}
