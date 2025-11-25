package io.github.codeonleo.leoshift.security.oauth2;

import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception ex) {
            log.error("OAuth2 사용자 처리 중 오류 발생", ex);
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, oAuth2User.getAttributes());

        if (!StringUtils.hasText(oAuth2UserInfo.getEmail())) {
            throw new OAuth2AuthenticationException("OAuth2 제공자로부터 이메일을 찾을 수 없습니다");
        }

        User user = userRepository.findByEmail(oAuth2UserInfo.getEmail())
                .map(existingUser -> updateExistingUser(existingUser, oAuth2UserInfo))
                .orElseGet(() -> registerNewUser(registrationId, oAuth2UserInfo));

        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }

    private User registerNewUser(String registrationId, OAuth2UserInfo oAuth2UserInfo) {
        User user = User.builder()
                .email(oAuth2UserInfo.getEmail())
                .name(oAuth2UserInfo.getName())
                .profileImageUrl(oAuth2UserInfo.getImageUrl())
                .provider(User.AuthProvider.valueOf(registrationId.toUpperCase()))
                .providerId(oAuth2UserInfo.getId())
                .roles(Set.of(User.Role.USER))
                .enabled(true)
                .build();

        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        existingUser.setName(oAuth2UserInfo.getName());
        existingUser.setProfileImageUrl(oAuth2UserInfo.getImageUrl());
        existingUser.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(existingUser);
    }
}
