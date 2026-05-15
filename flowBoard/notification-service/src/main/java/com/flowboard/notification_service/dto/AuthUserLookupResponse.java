package com.flowboard.notification_service.dto;

import lombok.Data;

@Data
public class AuthUserLookupResponse {
    private Long id;
    private String fullName;
    private String email;
}
