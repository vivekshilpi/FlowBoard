package com.flowboard.workspace_service.controller;

import com.flowboard.workspace_service.dto.*;
import com.flowboard.workspace_service.entity.WorkspaceInvitation;
import com.flowboard.workspace_service.entity.WorkspaceMember;
import com.flowboard.workspace_service.enums.MemberRole;
import com.flowboard.workspace_service.enums.Visibility;
import com.flowboard.workspace_service.exception.CustomException;
import com.flowboard.workspace_service.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    @Mock
    private WorkspaceService workspaceService;

    private WorkspaceController controller;
    private WorkspaceResponse response;

    @BeforeEach
    void setUp() {
        controller = new WorkspaceController(workspaceService);
        response = WorkspaceResponse.builder()
                .id(1L)
                .name("Dev Team")
                .ownerId(1L)
                .visibility(Visibility.PRIVATE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("create returns created response")
    void create_returnsCreatedResponse() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest();
        when(workspaceService.createWorkspace(request, 1L)).thenReturn(response);

        ResponseEntity<WorkspaceResponse> result = controller.create(request, 1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    @DisplayName("read endpoints delegate to service")
    void readEndpoints_delegateToService() {
        InvitationDetailsResponse invitationDetails = InvitationDetailsResponse.builder().id(9L).build();
        WorkspaceInvitation invitation = WorkspaceInvitation.builder().id(5L).build();
        WorkspaceMember member = WorkspaceMember.builder().id(8L).userId(2L).role(MemberRole.MEMBER).build();

        when(workspaceService.getById(1L, 2L, "MEMBER")).thenReturn(response);
        when(workspaceService.getAllWorkspaces()).thenReturn(List.of(response));
        when(workspaceService.getByOwner(1L)).thenReturn(List.of(response));
        when(workspaceService.getByMember(2L)).thenReturn(List.of(response));
        when(workspaceService.getPublicWorkspaces()).thenReturn(List.of(response));
        when(workspaceService.search("dev", 2L, "MEMBER")).thenReturn(List.of(response));
        when(workspaceService.getMembers(1L, 2L, "MEMBER")).thenReturn(List.of(member));
        when(workspaceService.getInvitationDetails(9L, 2L, "user@test.com", "MEMBER")).thenReturn(invitationDetails);
        when(workspaceService.getPendingInvitations(1L, 2L, "ADMIN")).thenReturn(List.of(invitation));

        assertThat(controller.getById(1L, 2L, "MEMBER").getBody()).isEqualTo(response);
        assertThat(controller.getAllWorkspaces("PLATFORM_ADMIN").getBody()).hasSize(1);
        assertThat(controller.getByOwner(1L).getBody()).hasSize(1);
        assertThat(controller.getByMember(2L).getBody()).hasSize(1);
        assertThat(controller.getPublic().getBody()).hasSize(1);
        assertThat(controller.search("dev", 2L, "MEMBER").getBody()).hasSize(1);
        assertThat(controller.getMembers(1L, 2L, "MEMBER").getBody()).hasSize(1);
        assertThat(controller.getInvitationDetails(9L, 2L, "user@test.com", "MEMBER").getBody())
                .isEqualTo(invitationDetails);
        assertThat(controller.getPendingInvitations(1L, 2L, "ADMIN").getBody()).hasSize(1);
    }

    @Test
    @DisplayName("write endpoints delegate to service")
    void writeEndpoints_delegateToService() {
        UpdateWorkspaceRequest updateWorkspaceRequest = new UpdateWorkspaceRequest();
        AddMemberRequest addMemberRequest = new AddMemberRequest();
        addMemberRequest.setUserId(3L);
        addMemberRequest.setRole(MemberRole.MEMBER);
        UpdateMemberRoleRequest updateMemberRoleRequest = new UpdateMemberRoleRequest();
        updateMemberRoleRequest.setRole(MemberRole.ADMIN);
        InviteMemberRequest inviteMemberRequest = new InviteMemberRequest();
        inviteMemberRequest.setEmail("new@example.com");
        inviteMemberRequest.setRole(MemberRole.MEMBER);
        InvitationActionResponse actionResponse = InvitationActionResponse.builder().invitationId(9L).status("ACCEPTED").build();
        WorkspaceMember member = WorkspaceMember.builder().id(8L).userId(3L).role(MemberRole.MEMBER).build();

        when(workspaceService.updateWorkspace(1L, updateWorkspaceRequest, 2L, "ADMIN")).thenReturn(response);
        when(workspaceService.addMember(1L, addMemberRequest, 2L, "ADMIN")).thenReturn(member);
        when(workspaceService.acceptInvitation(9L, 2L, "user@test.com")).thenReturn(actionResponse);
        when(workspaceService.rejectInvitation(9L, 2L, "user@test.com")).thenReturn(actionResponse);
        org.mockito.Mockito.doNothing().when(workspaceService).acceptInvitation("token", 2L);

        assertThat(controller.update(1L, updateWorkspaceRequest, 2L, "ADMIN").getBody()).isEqualTo(response);
        assertThat(controller.delete(1L, 2L, "ADMIN").getBody()).isEqualTo("Workspace deleted successfully");
        assertThat(controller.addMember(1L, addMemberRequest, 2L, "ADMIN").getBody()).isEqualTo(member);
        assertThat(controller.removeMember(1L, 3L, 2L, "ADMIN").getBody()).isEqualTo("Member removed successfully");
        assertThat(controller.updateMemberRole(1L, 3L, updateMemberRoleRequest, 2L, "ADMIN").getBody())
                .isEqualTo("Member role updated successfully");
        assertThat(controller.inviteMember(1L, inviteMemberRequest, 2L, "ADMIN").getBody())
                .isEqualTo("Invitation sent to new@example.com");
        assertThat(controller.acceptInvitation("token", 2L).getBody())
                .isEqualTo("Invitation accepted! You have joined the workspace.");
        assertThat(controller.acceptInvitationById(9L, 2L, "user@test.com").getBody()).isEqualTo(actionResponse);
        assertThat(controller.rejectInvitation(9L, 2L, "user@test.com").getBody()).isEqualTo(actionResponse);
        assertThat(controller.revokeInvitation(1L, 9L, 2L, "ADMIN").getBody()).isEqualTo("Invitation revoked");

        verify(workspaceService).deleteWorkspace(1L, 2L, "ADMIN");
        verify(workspaceService).removeMember(1L, 3L, 2L, "ADMIN");
        verify(workspaceService).updateMemberRole(1L, 3L, updateMemberRoleRequest, 2L, "ADMIN");
        verify(workspaceService).inviteMember(1L, inviteMemberRequest, 2L, "ADMIN");
        verify(workspaceService).acceptInvitation("token", 2L);
        verify(workspaceService).revokeInvitation(1L, 9L, 2L, "ADMIN");
    }

    @Test
    @DisplayName("missing user header and non-admin access throw errors")
    void helpers_throwOnMissingHeadersOrAccess() {
        assertThatThrownBy(() -> controller.create(new CreateWorkspaceRequest(), null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> controller.getAllWorkspaces("MEMBER"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
