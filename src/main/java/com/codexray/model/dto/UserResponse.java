package com.codexray.model.dto;

import com.codexray.model.entity.User;

public record UserResponse(
        Long id,
        String username,
        String nickname,
        String githubUsername,
        String avatarUrl,
        String token
) {
    public static UserResponse from(User user, String token) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getGithubUsername(),
                user.getAvatarUrl(),
                token
        );
    }
}
