import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';

import { WorkspaceDetailComponent } from './workspace-detail.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { BoardService } from '../../../core/services/board.service';
import { AuthService } from '../../../core/services/auth.service';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

describe('WorkspaceDetailComponent', () => {
  let component: WorkspaceDetailComponent;
  let workspaceService: jasmine.SpyObj<WorkspaceService>;
  let boardService: jasmine.SpyObj<BoardService>;
  let auth: jasmine.SpyObj<AuthService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;
  let snack: jasmine.SpyObj<MatSnackBar>;

  const workspace = {
    id: 1,
    name: 'Workspace',
    ownerId: 9,
    visibility: 'PRIVATE',
    members: [
      { userId: 9, role: 'OWNER' },
      { userId: 5, role: 'ADMIN' },
      { userId: 6, role: 'MEMBER' }
    ]
  } as any;

  const board = {
    id: 11,
    workspaceId: 1,
    name: 'Board',
    visibility: 'PRIVATE',
    members: [{ userId: 9, role: 'ADMIN' }]
  } as any;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkspaceDetailComponent],
      providers: [...createComponentTestingProviders({
        routeParams: { id: '1' }
      })]
    }).compileComponents();

    component = TestBed.createComponent(WorkspaceDetailComponent).componentInstance;
    workspaceService = TestBed.inject(WorkspaceService) as jasmine.SpyObj<WorkspaceService>;
    boardService = TestBed.inject(BoardService) as jasmine.SpyObj<BoardService>;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    dialog = TestBed.inject(DialogService) as jasmine.SpyObj<DialogService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    snack = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;

    spyOn(auth, 'getUserId').and.returnValue(9);
    spyOn(auth, 'isAdmin').and.returnValue(false);
    spyOn(auth as any, 'resolveAssetUrl').and.callFake((url: string | null | undefined) => url ? `resolved:${url}` : '');

    spyOn(workspaceService, 'getById').and.returnValue(of(workspace));
    spyOn(workspaceService, 'getPendingInvitations').and.returnValue(of([{ id: 3, email: 'invite@example.com' }] as any));
    spyOn(workspaceService, 'inviteMember').and.returnValue(of({} as any));
    spyOn(workspaceService, 'revokeInvitation').and.returnValue(of({} as any));
    spyOn(workspaceService, 'removeMember').and.returnValue(of({} as any));
    spyOn(workspaceService, 'updateMemberRole').and.returnValue(of({} as any));
    spyOn(workspaceService, 'update').and.returnValue(of({ ...workspace, visibility: 'PUBLIC' } as any));
    spyOn(workspaceService, 'delete').and.returnValue(of({} as any));

    spyOn(boardService, 'getByWorkspace').and.returnValue(of([board]));
    spyOn(boardService, 'create').and.returnValue(of({ id: 12, workspaceId: 1, name: 'Created Board' } as any));
    spyOn(boardService, 'delete').and.returnValue(of({} as any));
    spyOn(boardService, 'update').and.returnValue(of({ ...board, background: '#000000' } as any));

    spyOn(dialog as any, 'confirm').and.callFake(async (options: any) => {
      if (options?.onConfirm) {
        await options.onConfirm();
      }
      return true;
    });
    spyOn(router, 'navigate');
    spyOn(snack, 'open');
  });

  it('loads workspace boards and invitation data', () => {
    component.ngOnInit();
    component.loadInvitations();

    expect(auth.getUserId).toHaveBeenCalled();
    expect(workspaceService.getById).toHaveBeenCalledWith(1);
    expect(boardService.getByWorkspace).toHaveBeenCalledWith(1);
    expect(workspaceService.getPendingInvitations).toHaveBeenCalledWith(1);
    expect(component.workspace?.id).toBe(1);
    expect(component.boards.length).toBe(1);
    expect(component.invitations.length).toBe(1);
  });

  it('creates boards updates colors opens boards and revokes invitations', async () => {
    component.workspace = workspace;
    component.userId = 9;
    component.boards = [board];
    component.boardForm.setValue({
      name: 'Created Board',
      description: 'Desc',
      background: '#4f46e5',
      visibility: 'PRIVATE'
    });

    component.createBoard();
    expect(boardService.create).toHaveBeenCalled();
    expect(component.boards[0].id).toBe(12);

    component.openBoard(11);
    expect(router.navigate).toHaveBeenCalledWith(['/board', 11]);

    component.updateBoardColor(board, '#000000');
    expect(boardService.update).toHaveBeenCalled();

    component.invitations = [{ id: 3, email: 'invite@example.com' } as any];
    component.revokeInvitation(3);
    expect(workspaceService.revokeInvitation).toHaveBeenCalledWith(1, 3);
    expect(component.invitations).toEqual([]);

    await component.deleteBoard(11, new Event('click'));
    expect(boardService.delete).toHaveBeenCalledWith(11);
  });

  it('invites members updates roles and deletes the workspace', async () => {
    component.workspace = { ...workspace, members: [...workspace.members] };
    component.userId = 9;
    auth.isAdmin.and.returnValue(true);
    component.inviteForm.setValue({ email: 'new@example.com', role: 'MEMBER' });

    component.inviteMember();
    expect(workspaceService.inviteMember).toHaveBeenCalledWith(1, 'new@example.com', 'MEMBER');

    await component.removeMember(6);
    expect(workspaceService.removeMember).toHaveBeenCalledWith(1, 6);
    expect(component.workspace?.members.some((member: any) => member.userId === 6)).toBeFalse();

    component.workspace = { ...workspace, members: [...workspace.members] };
    await component.updateMemberRole(6, 'ADMIN');
    expect(workspaceService.updateMemberRole).toHaveBeenCalledWith(1, 6, 'ADMIN');

    component.updateWorkspaceVisibility('PUBLIC');
    expect(workspaceService.update).toHaveBeenCalledWith(1, { name: 'Workspace', visibility: 'PUBLIC' });

    await component.deleteWorkspace();
    expect(workspaceService.delete).toHaveBeenCalledWith(1);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('covers helper methods and error paths', () => {
    component.workspace = { ...workspace, members: [...workspace.members] };
    component.userId = 9;

    expect(component.getInitials('Flow Board')).toBe('FB');
    expect(component.getMemberAvatarUrl('/avatar.png')).toBe('resolved:/avatar.png');
    expect(component.isOwner()).toBeTrue();
    expect(component.isWorkspaceAdmin()).toBeFalse();
    expect(component.canManageMember({ userId: 6 } as any)).toBeFalse();
    expect(component.canManageBoard(board)).toBeTrue();

    auth.isAdmin.and.returnValue(true);
    expect(component.isWorkspaceAdmin()).toBeTrue();
    expect(component.canManageBoard({ ...board, members: [] })).toBeTrue();

    workspaceService.getById.and.returnValue(throwError(() => new Error('missing')));
    component.loadWorkspace(77);
    expect(component.loading).toBeFalse();

    boardService.getByWorkspace.and.returnValue(throwError(() => new Error('boards failed')));
    component.loadBoards(1);
    expect(component.loading).toBeFalse();

    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
