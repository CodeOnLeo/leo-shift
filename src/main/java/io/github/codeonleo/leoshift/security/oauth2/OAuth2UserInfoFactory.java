package io.github.codeonleo.leoshift.security.oauth2;

import io.github.codeonleo.leoshift.entity.User;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (registrationId.equalsIgnoreCase(User.AuthProvider.GOOGLE.toString())) {
            return new GoogleOAuth2UserInfo(attributes);
        } else {
            throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + registrationId);
        }
    }
}
