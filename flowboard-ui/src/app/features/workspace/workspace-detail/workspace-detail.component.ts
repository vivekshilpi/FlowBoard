import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { firstValueFrom } from 'rxjs';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { BoardService } from '../../../core/services/board.service';
import { AuthService } from '../../../core/services/auth.service';
import { Workspace } from '../../../core/models/workspace.model';
import { WorkspaceMember } from '../../../core/models/workspace.model';
import { Board } from '../../../core/models/board.model';
import { WorkspaceInvitation } from '../../../core/models/invitation.model';
import { ColorPickerComponent } from '../../../shared/components/color-picker/color-picker.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

@Component({
  selector: 'app-workspace-detail',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatMenuModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatChipsModule, ColorPickerComponent
  ],
  templateUrl: './workspace-detail.component.html',
  styleUrl: './workspace-detail.component.scss'
})
export class WorkspaceDetailComponent implements OnInit {

  private route            = inject(ActivatedRoute);
  private router           = inject(Router);
  private fb               = inject(FormBuilder);
  private workspaceService = inject(WorkspaceService);
  private boardService     = inject(BoardService);
  private auth             = inject(AuthService);
  private snack            = inject(MatSnackBar);
  private dialog           = inject(DialogService);

  workspace: Workspace | null = null;
  boards: Board[]             = [];
  invitations: WorkspaceInvitation[] = [];
  loading        = true;
  loadingInvitations = false;
  showCreateBoard = false;
  showInviteForm  = false;
  creatingBoard  = false;
  inviting       = false;
  userId         = 0;

  activeTab: 'boards' | 'members' | 'settings' = 'boards';

  boardForm = this.fb.group({
    name:       ['', [Validators.required, Validators.minLength(2)]],
    description:[''],
    background: ['#4f46e5'],
    visibility: ['PRIVATE', Validators.required]
  });

  inviteForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    role:  ['MEMBER', Validators.required]
  });

  get boardName() { return this.boardForm.get('name')!; }

  ngOnInit(): void {
    this.userId = this.auth.getUserId();
    const id    = Number(this.route.snapshot.paramMap.get('id'));
    this.loadWorkspace(id);
  }

  loadWorkspace(id: number): void {
    this.workspaceService.getById(id).subscribe({
      next: ws => {
        this.workspace = ws;
        this.loadBoards(id);
      },
      error: () => {
        this.loading = false;
        this.snack.open('Workspace not found', 'Close',
          { duration: 3000 });
      }
    });
  }

  loadBoards(workspaceId: number): void {
    this.boardService.getByWorkspace(workspaceId).subscribe({
      next: boards => {
        this.boards  = boards;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  createBoard(): void {
    if (this.boardForm.invalid) return;
    this.creatingBoard = true;

    this.boardService.create({
      workspaceId: this.workspace!.id,
      ...this.boardForm.value
    } as any).subscribe({
      next: board => {
        this.boards.unshift(board);
        this.creatingBoard   = false;
        this.showCreateBoard = false;
        this.boardForm.reset({
          background: '#4f46e5', visibility: 'PRIVATE'
        });
        this.snack.open('Board created!', 'Close',
          { duration: 3000 });
      },
      error: err => {
        this.creatingBoard = false;
        this.snack.open(
          err.error?.message ?? 'Create failed',
          'Close', { duration: 4000 });
      }
    });
  }

  openBoard(id: number): void {
    this.router.navigate(['/board', id]);
  }

  async deleteBoard(id: number, e: Event): Promise<void> {
    e.stopPropagation();
    const board = this.boards.find(candidate => candidate.id === id);
    if (!board || !this.canManageBoard(board)) return;
    const confirmed = await this.dialog.confirm({
      title: 'Delete board?',
      description: `This will permanently remove "${board.name}" from the workspace.`,
      confirmText: 'Delete board',
      cancelText: 'Keep board',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.boardService.delete(id)),
      errorMessage: 'Failed to delete board'
    });
    if (!confirmed) return;

    this.boards = this.boards.filter(b => b.id !== id);
    this.snack.open('Board deleted', 'Close', { duration: 3000 });
  }

  updateBoardColor(board: Board, color: string | null): void {
    if (!color || !this.canManageBoard(board)) return;
    this.boardService.update(board.id, {
      name: board.name,
      description: board.description || undefined,
      background: color,
      visibility: board.visibility
    }).subscribe({
      next: updated => {
        board.background = updated.background;
        this.snack.open('Board theme updated', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to update board theme', 'Close', { duration: 3000 })
    });
  }

  loadInvitations(): void {
    if (!this.workspace) return;
    this.loadingInvitations = true;
    this.workspaceService.getPendingInvitations(this.workspace.id).subscribe({
      next: invites => {
        this.invitations = invites;
        this.loadingInvitations = false;
      },
      error: () => { this.loadingInvitations = false; }
    });
  }

  inviteMember(): void {
    if (this.inviteForm.invalid || !this.workspace) return;
    this.inviting = true;
    const { email, role } = this.inviteForm.value;

    this.workspaceService.inviteMember(this.workspace.id, email!, role!).subscribe({
      next: () => {
        this.inviting = false;
        this.showInviteForm = false;
        this.inviteForm.reset({ role: 'MEMBER' });
        this.snack.open(`Invitation sent to ${email}`, 'Close', { duration: 3000 });
        this.loadInvitations();
      },
      error: err => {
        this.inviting = false;
        this.snack.open(err.error?.message ?? 'Invitation failed', 'Close', { duration: 4000 });
      }
    });
  }

  revokeInvitation(inviteId: number): void {
    if (!this.workspace) return;
    this.workspaceService.revokeInvitation(this.workspace.id, inviteId).subscribe({
      next: () => {
        this.invitations = this.invitations.filter(i => i.id !== inviteId);
        this.snack.open('Invitation revoked', 'Close', { duration: 2000 });
      }
    });
  }

  async removeMember(userId: number): Promise<void> {
    if (!this.workspace) return;
    const confirmed = await this.dialog.confirm({
      title: 'Remove member?',
      description: 'They will lose access to this workspace immediately.',
      confirmText: 'Remove member',
      cancelText: 'Keep member',
      variant: 'warning',
      onConfirm: () => firstValueFrom(this.workspaceService.removeMember(this.workspace!.id, userId)),
      errorMessage: 'Failed to remove member'
    });
    if (!confirmed) return;
    if (this.workspace) {
      this.workspace.members = this.workspace.members.filter((m: any) => m.userId !== userId);
    }
    this.snack.open('Member removed', 'Close', { duration: 2000 });
  }

  async updateMemberRole(userId: number, role: 'ADMIN' | 'MEMBER'): Promise<void> {
    if (!this.workspace) return;
    const actionLabel = role === 'ADMIN' ? 'make this member an admin' : 'change this admin back to member';
    const confirmed = await this.dialog.confirm({
      title: 'Update member role?',
      description: `Are you sure you want to ${actionLabel}?`,
      confirmText: role === 'ADMIN' ? 'Promote to admin' : 'Change to member',
      cancelText: 'Cancel',
      variant: role === 'ADMIN' ? 'info' : 'warning',
      onConfirm: () => firstValueFrom(this.workspaceService.updateMemberRole(this.workspace!.id, userId, role)),
      errorMessage: (err) => (err as any)?.error?.message ?? 'Failed to update member role'
    });
    if (!confirmed) return;

    if (this.workspace) {
      this.workspace.members = this.workspace.members.map(member =>
        member.userId === userId ? { ...member, role } : member
      );
    }
    this.snack.open(
      role === 'ADMIN' ? 'Member promoted to admin' : 'Admin changed to member',
      'Close',
      { duration: 2500 }
    );
  }

  updateWorkspaceVisibility(visibility: 'PUBLIC' | 'PRIVATE'): void {
    if (!this.workspace || !this.isWorkspaceAdmin()) return;
    this.workspaceService.update(this.workspace.id, {
      name: this.workspace.name,
      visibility 
    }).subscribe({
      next: updated => {
        this.workspace = updated;
        this.snack.open(`Workspace is now ${visibility}`, 'Close', { duration: 3000 });
      },
      error: () => this.snack.open('Failed to update visibility', 'Close', { duration: 3000 })
    });
  }

  async deleteWorkspace(): Promise<void> {
    if (!this.workspace || !this.isWorkspaceAdmin()) return;
    const confirmed = await this.dialog.confirm({
      title: 'Delete workspace permanently?',
      description: 'This will permanently delete the workspace, all boards, and member access. This action cannot be undone.',
      confirmText: 'Delete workspace',
      cancelText: 'Keep workspace',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.workspaceService.delete(this.workspace!.id)),
      errorMessage: 'Failed to delete workspace'
    });
    if (!confirmed) return;

    this.snack.open('Workspace deleted', 'Close', { duration: 3000 });
    this.router.navigate(['/dashboard']);
  }

  goBack(): void { this.router.navigate(['/dashboard']); }

  getInitials(name: string): string {
    return name.split(' ')
      .map(n => n[0]).join('').toUpperCase().slice(0, 2);
  }

  getMemberAvatarUrl(avatarUrl: string | null | undefined): string {
    return this.auth.resolveAssetUrl(avatarUrl);
  }

  isOwner(): boolean {
    return this.workspace?.ownerId === this.userId;
  }

  isWorkspaceAdmin(): boolean {
    if (this.auth.isAdmin()) {
      return true;
    }

    if (!this.workspace) {
      return false;
    }

    return this.workspace.members.some(member =>
      member.userId === this.userId && member.role === 'ADMIN'
    );
  }

  canManageMember(member: WorkspaceMember): boolean {
    return this.isWorkspaceAdmin()
      && !!this.workspace
      && member.userId !== this.workspace.ownerId
      && member.userId !== this.userId;
  }

  canManageBoard(board: Board): boolean {
    if (this.auth.isAdmin()) {
      return true;
    }

    return board.members.some(member =>
      member.userId === this.userId && member.role === 'ADMIN'
    );
  }
}
