package com.flowboard.workspace_service.service;

import com.flowboard.workspace_service.config.RabbitMQConfig;
import com.flowboard.workspace_service.dto.AuthUserLookupResponse;
import com.flowboard.workspace_service.dto.*;
import com.flowboard.workspace_service.entity.Workspace;
import com.flowboard.workspace_service.entity.WorkspaceInvitation;
import com.flowboard.workspace_service.entity.WorkspaceMember;
import com.flowboard.workspace_service.enums.MemberRole;
import com.flowboard.workspace_service.enums.Visibility;
import com.flowboard.workspace_service.event.WorkspaceInviteEvent;
import com.flowboard.workspace_service.exception.CustomException;
import com.flowboard.workspace_service.repository.WorkspaceInvitationRepository;
import com.flowboard.workspace_service.repository.WorkspaceMemberRepository;
import com.flowboard.workspace_service.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService{

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceInvitationRepository invitationRepository;

    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;


    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, Long ownerId){

        if(workspaceRepository.existsByNameAndOwnerId(request.getName(), ownerId)){
            throw new CustomException("You already have a workspace named '"+request.getName()+"'",
                    HttpStatus.BAD_REQUEST);
        }

        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .visibility(request.getVisibility()!=null ?request.getVisibility(): Visibility.PRIVATE)
                .logoUrl(request.getLogoUrl())
                .createdAt(LocalDateTime.now())
                .build();

        workspaceRepository.save(workspace);

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspace(workspace)
                .userId(ownerId)
                .role(MemberRole.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        memberRepository.save(ownerMember);

        log.info("Workspace created: id={} name={} owner={}", workspace.getId(), workspace.getName(), ownerId);
        return toResponse(workspace);
    }

    @Override
    @Transactional
    public void inviteMember(Long workspaceId,
                             InviteMemberRequest request,
                             Long invitedBy,
                             String userRole) {

        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId, invitedBy, userRole);
        if (request.getRole() == MemberRole.ADMIN) {
            requireOwnerOrPlatformAdmin(
                    workspace,
                    invitedBy,
                    userRole,
                    "Only the workspace owner can invite another admin"
            );
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Prevent duplicate pending invitations
        if (invitationRepository.existsByWorkspaceIdAndInviteeEmailAndStatus(
                workspaceId, normalizedEmail, "PENDING")) {
            throw new CustomException(
                    "A pending invitation already exists for this email",
                    HttpStatus.BAD_REQUEST);
        }

        Long inviteeUserId = resolveInviteeUserId(normalizedEmail);
        if (inviteeUserId != null && memberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteeUserId)) {
            throw new CustomException(
                    "That user is already a member of this workspace",
                    HttpStatus.BAD_REQUEST);
        }

        AuthUserLookupResponse inviter = lookupUserById(invitedBy);

        String token = UUID.randomUUID().toString();
        String acceptUrl = "http://localhost:4200/invite/accept?token=" + token;

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspaceId(workspaceId)
                .invitedBy(invitedBy)
                .inviterName(inviter.getFullName())
                .inviterEmail(inviter.getEmail())
                .inviteeEmail(normalizedEmail)
                .role(request.getRole())
                .token(token)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        invitationRepository.save(invitation);

        // Publish to RabbitMQ → notification-service (or workspace) listens
        // and sends the invitation email
        WorkspaceInviteEvent event = new WorkspaceInviteEvent(
                invitation.getId(),
                workspaceId,
                workspace.getName(),
                inviter.getFullName(),
                inviter.getEmail(),
                normalizedEmail,
                inviteeUserId,
                token,
                request.getRole().name(),
                invitedBy,
                acceptUrl
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FLOWBOARD_EXCHANGE,
                RabbitMQConfig.INVITE_KEY,
                event
        );

        log.info("Invitation sent: workspaceId={} email={} role={}",
                workspaceId, normalizedEmail, request.getRole());
    }

    private Long resolveInviteeUserId(String email) {
        try {
            ResponseEntity<AuthUserLookupResponse> response = restTemplate.getForEntity(
                    "http://localhost:8081/api/v1/auth/internal/by-email?email={email}",
                    AuthUserLookupResponse.class,
                    email
            );

            AuthUserLookupResponse user = response.getBody();
            if (user != null && user.getId() != null) {
                return user.getId();
            }
        } catch (RestClientException e) {
            log.info("Invitee {} not found in auth-service; email invite will still be sent", email);
        }

        return null;
    }

    @Override
    @Transactional
    public void acceptInvitation(String token, Long userId) {
        WorkspaceInvitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> new CustomException(
                        "Invalid invitation token", HttpStatus.NOT_FOUND));
        assertInvitationInvitee(inv, lookupUserById(userId).getEmail());
        acceptInvitationInternal(inv, userId);
    }

    @Override
    public InvitationDetailsResponse getInvitationDetails(Long invitationId,
                                                          Long requesterId,
                                                          String requesterEmail,
                                                          String userRole) {
        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(
                        "Invitation not found", HttpStatus.NOT_FOUND));

        expireInvitationIfNeeded(invitation);
        assertInvitationAccess(invitation, requesterId, requesterEmail, userRole);
        invitation = hydrateInviterMetadataIfMissing(invitation);

        Workspace workspace = findWorkspace(invitation.getWorkspaceId());
        return InvitationDetailsResponse.builder()
                .id(invitation.getId())
                .workspaceId(workspace.getId())
                .workspaceName(workspace.getName())
                .inviterId(invitation.getInvitedBy())
                .inviterName(invitation.getInviterName())
                .inviterEmail(invitation.getInviterEmail())
                .inviteeEmail(invitation.getInviteeEmail())
                .role(invitation.getRole())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .build();
    }

    @Override
    @Transactional
    public InvitationActionResponse acceptInvitation(Long invitationId,
                                                     Long requesterId,
                                                     String requesterEmail) {
        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(
                        "Invitation not found", HttpStatus.NOT_FOUND));

        assertInvitationInvitee(invitation, requesterEmail);
        Workspace workspace = acceptInvitationInternal(invitation, requesterId);

        return InvitationActionResponse.builder()
                .invitationId(invitation.getId())
                .workspaceId(workspace.getId())
                .workspaceName(workspace.getName())
                .status(invitation.getStatus())
                .message("Invitation accepted. Workspace added to your dashboard.")
                .build();
    }

    @Override
    @Transactional
    public InvitationActionResponse rejectInvitation(Long invitationId,
                                                     Long requesterId,
                                                     String requesterEmail) {
        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(
                        "Invitation not found", HttpStatus.NOT_FOUND));

        assertInvitationInvitee(invitation, requesterEmail);
        ensurePendingInvitation(invitation);

        Workspace workspace = findWorkspace(invitation.getWorkspaceId());
        invitation.setStatus("REJECTED");
        invitationRepository.save(invitation);
        cleanupInviteNotification(invitation.getInviteeEmail(), invitation.getWorkspaceId());
        notifyInviterOfDecision(invitation, workspace.getName(), requesterId, false);

        return InvitationActionResponse.builder()
                .invitationId(invitation.getId())
                .workspaceId(workspace.getId())
                .workspaceName(workspace.getName())
                .status(invitation.getStatus())
                .message("Invitation ignored.")
                .build();
    }

    @Override
    @Transactional
    public void revokeInvitation(Long workspaceId, Long invitationId,
                                 Long requesterId, String userRole) {
        requireAdmin(workspaceId, requesterId, userRole);

        WorkspaceInvitation inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(
                        "Invitation not found", HttpStatus.NOT_FOUND));

        if (!inv.getWorkspaceId().equals(workspaceId)) {
            throw new CustomException(
                    "Invitation does not belong to this workspace",
                    HttpStatus.BAD_REQUEST);
        }

        if (!"PENDING".equals(inv.getStatus())) {
            throw new CustomException(
                    "Only pending invitations can be revoked",
                    HttpStatus.BAD_REQUEST);
        }

        inv.setStatus("REVOKED");
        invitationRepository.save(inv);
        cleanupInviteNotification(inv.getInviteeEmail(), workspaceId);
        log.info("Invitation revoked: id={} by userId={}", invitationId, requesterId);
    }

    @Override
    public List<WorkspaceInvitation> getPendingInvitations(Long workspaceId,
                                                           Long requesterId,
                                                           String userRole) {
        requireAdmin(workspaceId, requesterId, userRole);
        return invitationRepository.findByWorkspaceIdAndStatus(
                workspaceId, "PENDING").stream()
                .filter(invitation -> {
                    expireInvitationIfNeeded(invitation);
                    return "PENDING".equals(invitation.getStatus());
                })
                .toList();
    }

    @Override
    public List<WorkspaceResponse> getAllWorkspaces() {
        return workspaceRepository.findAll().stream().map(this::toResponse).toList();
    }


    @Override
    public WorkspaceResponse getById(Long workspaceId, Long requesterId, String userRole){
        Workspace workspace = findWorkspace(workspaceId);

        if(workspace.getVisibility()==Visibility.PRIVATE){
            requireMember(workspaceId, requesterId, userRole);
        }
        return toResponse(workspace);
    }

    @Override
    public List<WorkspaceResponse> getByOwner(Long ownerId){
        return workspaceRepository.findByOwnerId(ownerId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<WorkspaceResponse> getByMember(Long userId){
        return workspaceRepository.findByMemberUserId(userId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<WorkspaceResponse> getPublicWorkspaces() {
        return workspaceRepository.findByVisibility(Visibility.PUBLIC)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<WorkspaceResponse> search(String keyword, Long requesterId, String userRole) {
        return workspaceRepository.search(keyword).stream()
                .filter(workspace -> workspace.getVisibility() == Visibility.PUBLIC
                        || "PLATFORM_ADMIN".equals(userRole)
                        || memberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), requesterId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId,
                                             UpdateWorkspaceRequest request,
                                             Long requesterId,
                                             String userRole) {
        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId, requesterId, userRole);

        workspace.setName(request.getName());
        workspace.setDescription(request.getDescription());

        if (request.getVisibility() != null) {
            workspace.setVisibility(request.getVisibility());
        }
        if (request.getLogoUrl() != null) {
            workspace.setLogoUrl(request.getLogoUrl());
        }

        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceRepository.save(workspace);

        log.info("Workspace updated: id={}", workspaceId);
        return toResponse(workspace);
    }

    @Override
    @Transactional
    public void deleteWorkspace(Long workspaceId, Long requesterId, String userRole){
        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId, requesterId, userRole);
        requireOwnerOrPlatformAdmin(
                workspace,
                requesterId,
                userRole,
                "Only the workspace owner can delete this workspace"
        );

        workspaceRepository.delete(workspace);
        cleanupWorkspaceNotifications(workspaceId);
        log.info("Workspace deleted: id={}", workspaceId);
    }

    private void cleanupWorkspaceNotifications(Long workspaceId) {
        try {
            restTemplate.delete(
                    "http://localhost:8086/api/v1/notifications/related?relatedId={relatedId}&relatedType={relatedType}",
                    workspaceId,
                    "WORKSPACE"
            );
        } catch (RestClientException e) {
            log.warn("Failed to clean notifications for workspaceId={}: {}", workspaceId, e.getMessage());
        }
    }

    private void cleanupInviteNotification(String inviteeEmail, Long workspaceId) {
        Long recipientId = resolveInviteeUserId(inviteeEmail);
        if (recipientId == null) {
            return;
        }

        try {
            restTemplate.delete(
                    "http://localhost:8086/api/v1/notifications/workspace-invite?recipientId={recipientId}&workspaceId={workspaceId}",
                    recipientId,
                    workspaceId
            );
        } catch (RestClientException e) {
            log.warn("Failed to clean invite notification for recipientId={} workspaceId={}: {}",
                    recipientId, workspaceId, e.getMessage());
        }
    }

    private Workspace acceptInvitationInternal(WorkspaceInvitation invitation, Long userId) {
        ensurePendingInvitation(invitation);

        Workspace workspace = findWorkspace(invitation.getWorkspaceId());

        if (!memberRepository.existsByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId)) {
            WorkspaceMember member = WorkspaceMember.builder()
                    .workspace(workspace)
                    .userId(userId)
                    .role(invitation.getRole())
                    .joinedAt(LocalDateTime.now())
                    .build();
            memberRepository.save(member);
        }

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        cleanupInviteNotification(invitation.getInviteeEmail(), invitation.getWorkspaceId());
        notifyInviterOfDecision(invitation, workspace.getName(), userId, true);

        log.info("Invitation accepted: userId={} workspaceId={} role={}",
                userId, invitation.getWorkspaceId(), invitation.getRole());
        return workspace;
    }

    private void ensurePendingInvitation(WorkspaceInvitation invitation) {
        expireInvitationIfNeeded(invitation);

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new CustomException(
                    switch (invitation.getStatus()) {
                        case "EXPIRED" -> "Invitation has expired. Please request a new one.";
                        case "ACCEPTED" -> "Invitation has already been accepted.";
                        case "REJECTED" -> "Invitation has already been rejected.";
                        case "REVOKED" -> "Invitation has been revoked.";
                        default -> "This invitation is no longer valid";
                    },
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void expireInvitationIfNeeded(WorkspaceInvitation invitation) {
        if ("PENDING".equals(invitation.getStatus())
                && invitation.getExpiresAt() != null
                && LocalDateTime.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
        }
    }

    private void assertInvitationAccess(WorkspaceInvitation invitation,
                                        Long requesterId,
                                        String requesterEmail,
                                        String userRole) {
        if ("PLATFORM_ADMIN".equals(userRole) || invitation.getInvitedBy().equals(requesterId)) {
            return;
        }
        assertInvitationInvitee(invitation, requesterEmail);
    }

    private void assertInvitationInvitee(WorkspaceInvitation invitation, String requesterEmail) {
        if (requesterEmail == null || requesterEmail.isBlank()) {
            throw new CustomException(
                    "X-User-Email header is required",
                    HttpStatus.BAD_REQUEST);
        }

        if (!invitation.getInviteeEmail().equalsIgnoreCase(requesterEmail.trim())) {
            throw new CustomException(
                    "You are not authorized to access this invitation",
                    HttpStatus.FORBIDDEN);
        }
    }

    private WorkspaceInvitation hydrateInviterMetadataIfMissing(WorkspaceInvitation invitation) {
        if (!needsInviterMetadataRepair(invitation)) {
            return invitation;
        }

        AuthUserLookupResponse inviter = lookupUserById(invitation.getInvitedBy());
        if (isFallbackInviter(inviter)) {
            return invitation;
        }

        invitation.setInviterName(inviter.getFullName());
        invitation.setInviterEmail(inviter.getEmail());
        return invitationRepository.save(invitation);
    }

    private boolean needsInviterMetadataRepair(WorkspaceInvitation invitation) {
        String inviterName = invitation.getInviterName();
        String inviterEmail = invitation.getInviterEmail();

        return inviterName == null
                || inviterName.isBlank()
                || "FlowBoard User".equalsIgnoreCase(inviterName.trim())
                || inviterEmail == null
                || inviterEmail.isBlank()
                || "Unavailable".equalsIgnoreCase(inviterEmail.trim())
                || "unavailable@flowboard.local".equalsIgnoreCase(inviterEmail.trim());
    }

    private boolean isFallbackInviter(AuthUserLookupResponse inviter) {
        return inviter == null
                || inviter.getFullName() == null
                || inviter.getFullName().isBlank()
                || "FlowBoard User".equalsIgnoreCase(inviter.getFullName().trim())
                || inviter.getEmail() == null
                || inviter.getEmail().isBlank()
                || "unavailable@flowboard.local".equalsIgnoreCase(inviter.getEmail().trim());
    }

    private AuthUserLookupResponse lookupUserById(Long userId) {
        try {
            ResponseEntity<AuthUserLookupResponse> response = restTemplate.getForEntity(
                    "http://localhost:8081/api/v1/auth/internal/by-id?id={id}",
                    AuthUserLookupResponse.class,
                    userId
            );

            AuthUserLookupResponse user = response.getBody();
            if (user != null) {
                return user;
            }
        } catch (RestClientException e) {
            log.warn("Failed to resolve user {} from auth-service: {}", userId, e.getMessage());
        }

        AuthUserLookupResponse fallback = new AuthUserLookupResponse();
        fallback.setId(userId);
        fallback.setFullName("FlowBoard User");
        fallback.setUsername("flowboard-user");
        fallback.setEmail("unavailable@flowboard.local");
        fallback.setAvatarUrl(null);
        return fallback;
    }

    private void notifyInviterOfDecision(WorkspaceInvitation invitation,
                                         String workspaceName,
                                         Long actorUserId,
                                         boolean accepted) {
        try {
            SendNotificationRequest request = new SendNotificationRequest();
            request.setRecipientId(invitation.getInvitedBy());
            request.setActorId(actorUserId);
            request.setType(accepted ? NotificationType.WORKSPACE_INVITE_ACCEPTED
                    : NotificationType.WORKSPACE_INVITE_REJECTED);
            request.setTitle(accepted ? "Workspace invitation accepted" : "Workspace invitation rejected");
            request.setMessage(invitation.getInviteeEmail()
                    + (accepted ? " accepted " : " rejected ")
                    + "your invitation to join '" + workspaceName + "'.");
            request.setRelatedId(invitation.getWorkspaceId());
            request.setRelatedType("WORKSPACE");
            request.setDeepLinkUrl("/workspace/" + invitation.getWorkspaceId());
            request.setSendEmail(false);

            restTemplate.postForEntity(
                    "http://localhost:8086/api/v1/notifications/send",
                    request,
                    String.class
            );
        } catch (RestClientException e) {
            log.warn("Failed to notify inviter {} for invitation {}: {}",
                    invitation.getInvitedBy(), invitation.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public WorkspaceMember addMember(Long workspaceId,
                                     AddMemberRequest request,
                                     Long requesterId,
                                     String userRole){
        requireAdmin(workspaceId, requesterId, userRole);

        if(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.getUserId())){
            throw new CustomException("User is already a member of this workspace", HttpStatus.BAD_REQUEST);
        }

        Workspace workspace = findWorkspace(workspaceId);
        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .userId(request.getUserId())
                .role(request.getRole()!=null ? request.getRole() : MemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
        memberRepository.save(member);
        log.info("Member added: workspaceId={} userId={} role={}", workspaceId, request.getUserId(), member.getRole());
        return member;
    }

    @Override
    @Transactional
    public void removeMember(Long workspaceId, Long userId, Long requesterid, String userRole){
        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId, requesterid, userRole);
        requireOwnerOrPlatformAdmin(
                workspace,
                requesterid,
                userRole,
                "Only the workspace owner can remove members"
        );

        if(!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)){
            throw new CustomException("User is not a member of this workspace", HttpStatus.NOT_FOUND);
        }

        if(workspace.getOwnerId().equals(userId)){
            throw new CustomException("Cannot remove the workspace owner", HttpStatus.BAD_REQUEST);
        }

        memberRepository.deleteByWorkspaceIdAndUserId(workspaceId, userId);
        log.info("Member removed: workspaceId={} userId={}",workspaceId, userId);
    }

    @Override
    @Transactional
    public void updateMemberRole(Long workspaceId, Long userId,
                                 UpdateMemberRoleRequest request, Long requesterId, String userRole){
        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId, requesterId, userRole);
        requireOwnerOrPlatformAdmin(
                workspace,
                requesterId,
                userRole,
                "Only the workspace owner can change member roles"
        );

        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(()-> new CustomException("User is not the member of this workspace", HttpStatus.NOT_FOUND));

        if (workspace.getOwnerId().equals(userId)) {
            throw new CustomException("Cannot change the workspace owner's role", HttpStatus.BAD_REQUEST);
        }

        member.setRole(request.getRole());
        memberRepository.save(member);
        log.info("Member role updated: workspaceId={} userId={} newRole={}", workspaceId, userId, request.getRole());
    }

    @Override
    public List<WorkspaceMember> getMembers(Long workspaceId, Long requesterId, String userRole){
        findWorkspace(workspaceId);
        requireMember(workspaceId, requesterId, userRole);
        return memberRepository.findByWorkspaceId(workspaceId);
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new CustomException(
                        "Workspace not found", HttpStatus.NOT_FOUND));
    }

    private void requireMember(Long workspaceId, Long userId, String userRole) {
        if ("PLATFORM_ADMIN".equals(userRole)) return;
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new CustomException("Access denied — you are not a member of this workspace",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void requireAdmin(Long workspaceId, Long userId, String userRole) {
        if ("PLATFORM_ADMIN".equals(userRole)) return;
        WorkspaceMember member = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new CustomException(
                        "Access denied — you are not a member of this workspace", HttpStatus.FORBIDDEN));

        if (member.getRole() != MemberRole.ADMIN) {
            throw new CustomException("Access denied — admin role required", HttpStatus.FORBIDDEN);
        }
    }

    private void requireOwnerOrPlatformAdmin(Workspace workspace,
                                             Long userId,
                                             String userRole,
                                             String message) {
        if ("PLATFORM_ADMIN".equals(userRole)) {
            return;
        }
        if (!workspace.getOwnerId().equals(userId)) {
            throw new CustomException(message, HttpStatus.FORBIDDEN);
        }
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        Map<Long, AuthUserLookupResponse> userCache = new HashMap<>();
        List<WorkspaceResponse.MemberDto> memberDtos = memberRepository
                .findByWorkspaceId(workspace.getId())
                .stream()
                .map(m -> {
                    AuthUserLookupResponse user = userCache.computeIfAbsent(
                            m.getUserId(),
                            this::lookupUserById
                    );

                    return WorkspaceResponse.MemberDto.builder()
                            .userId(m.getUserId())
                            .role(m.getRole())
                            .joinedAt(m.getJoinedAt())
                            .user(WorkspaceResponse.UserSummaryDto.builder()
                                    .id(user.getId())
                                    .fullName(user.getFullName())
                                    .username(user.getUsername())
                                    .email(user.getEmail())
                                    .avatarUrl(user.getAvatarUrl())
                                    .build())
                            .build();
                })
                .toList();

        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .ownerId(workspace.getOwnerId())
                .visibility(workspace.getVisibility())
                .logoUrl(workspace.getLogoUrl())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .members(memberDtos)
                .build();
    }
}
