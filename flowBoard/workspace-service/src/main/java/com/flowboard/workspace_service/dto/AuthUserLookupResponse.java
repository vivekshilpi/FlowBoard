package com.flowboard.workspace_service.dto;

import lombok.Data;

@Data
public class AuthUserLookupResponse {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String avatarUrl;
}
