package com.flowBoard.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupResponse {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String avatarUrl;
}
