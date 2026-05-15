import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { combineLatest, filter, map, of, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import * as AdminActions from '../../../store/admin/admin.actions';
import * as AdminSelectors from '../../../store/admin/admin.selectors';
import { AuthService } from '../../../core/services/auth.service';
import { CardService } from '../../../core/services/card.service';
import { Card } from '../../../core/models/card.model';
import { Board } from '../../../core/models/board.model';
import { Workspace } from '../../../core/models/workspace.model';
import { UserProfile } from '../../../core/models/user.model';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTabsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatMenuModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    FormsModule,
    MatSnackBarModule
  ],
  templateUrl: './admin-panel.component.html',
  styleUrl: './admin-panel.component.scss'
})
export class AdminPanelComponent implements OnInit {
  private store = inject(Store);
  private snack = inject(MatSnackBar);
  private authService = inject(AuthService);
  private cardService = inject(CardService);
  private dialog = inject(DialogService);

  users$   = this.store.select(AdminSelectors.selectAdminUsers);
  workspaces$ = this.store.select(AdminSelectors.selectAdminWorkspaces);
  boards$ = this.store.select(AdminSelectors.selectAdminBoards);
  stats$   = this.store.select(AdminSelectors.selectAdminStats);
  loading$ = this.store.select(AdminSelectors.selectAdminLoading);
  message$ = this.store.select(AdminSelectors.selectAdminMessage);
  error$ = this.store.select(AdminSelectors.selectAdminError);
  overdueCards$ = this.cardService.getAllOverdue().pipe(catchError(() => of([] as Card[])));

  readonly summaryCards$ = combineLatest([
    this.stats$,
    this.workspaces$,
    this.boards$,
    this.overdueCards$
  ]).pipe(
    map(([stats, workspaces, boards, overdueCards]) => {
      const activeTeams = workspaces.filter(workspace => workspace.members.length >= 3).length;
      const openBoards = boards.filter(board => !board.isClosed).length;
      return [
        {
          icon: 'groups',
          value: stats?.totalUsers ?? 0,
          label: 'Platform users',
          tone: 'accent',
          helper: `${stats?.platformAdmins ?? 0} admins, ${stats?.members ?? 0} members`
        },
        {
          icon: 'space_dashboard',
          value: stats?.totalBoards ?? 0,
          label: 'Boards at scale',
          tone: 'neutral',
          helper: `${openBoards} currently open`
        },
        {
          icon: 'apartment',
          value: activeTeams,
          label: 'Active teams',
          tone: 'neutral',
          helper: `${workspaces.length} workspaces tracked`
        },
        {
          icon: 'warning',
          value: overdueCards.length,
          label: 'Overdue cards',
          tone: overdueCards.length ? 'danger' : 'success',
          helper: overdueCards.length ? 'Needs SLA attention' : 'No SLA issues detected'
        }
      ];
    })
  );

  readonly platformActivity$ = combineLatest([
    this.users$,
    this.workspaces$,
    this.boards$
  ]).pipe(
    map(([users, workspaces, boards]) => this.buildActivityFeed(users, workspaces, boards))
  );

  readonly overdueCardsWithContext$ = combineLatest([
    this.overdueCards$,
    this.boards$,
    this.workspaces$
  ]).pipe(
    map(([cards, boards, workspaces]) =>
      cards
        .slice()
        .sort((a, b) => new Date(a.dueDate || 0).getTime() - new Date(b.dueDate || 0).getTime())
        .slice(0, 6)
        .map(card => {
          const board = boards.find(item => item.id === card.boardId);
          const workspace = workspaces.find(item => item.id === board?.workspaceId);
          return {
            ...card,
            boardName: board?.name ?? `Board #${card.boardId}`,
            workspaceName: workspace?.name ?? 'Workspace unavailable'
          };
        })
    )
  );

  readonly workspaceInsights$ = combineLatest([
    this.workspaces$,
    this.boards$
  ]).pipe(
    map(([workspaces, boards]) => {
      const avgBoardsPerWorkspace = workspaces.length ? (boards.length / workspaces.length).toFixed(1) : '0.0';
      const publicExposure = workspaces.length ? Math.round((workspaces.filter(item => item.visibility === 'PUBLIC').length / workspaces.length) * 100) : 0;
      return {
        avgBoardsPerWorkspace,
        publicExposure,
        largestWorkspace: workspaces
          .slice()
          .sort((a, b) => b.members.length - a.members.length)[0]
      };
    })
  );

  userColumns = ['avatar', 'name', 'email', 'status', 'role', 'actions'];
  workspaceColumns = ['name', 'owner', 'visibility', 'members', 'updatedAt', 'actions'];
  boardColumns = ['name', 'workspace', 'visibility', 'status', 'members', 'updatedAt', 'actions'];
  broadcastAudience = 'all-users';
  broadcastMessage = '';
  users: UserProfile[] = [];
  workspaces: Workspace[] = [];
  boards: Board[] = [];

  ngOnInit(): void {
    this.store.dispatch(AdminActions.loadAdminStats());
    this.store.dispatch(AdminActions.loadAllUsers());
    this.store.dispatch(AdminActions.loadAllWorkspaces());
    this.store.dispatch(AdminActions.loadAllBoards());

    this.message$.pipe(
      filter((message): message is string => !!message),
      takeUntilDestroyed()
    ).subscribe(message => {
      this.snack.open(message, 'Close', { duration: 3000 });
      this.store.dispatch(AdminActions.clearAdminMessage());
    });

    this.error$.pipe(
      filter((error): error is string => !!error),
      takeUntilDestroyed()
    ).subscribe(error => {
      this.snack.open(error, 'Close', { duration: 4000 });
    });

    this.users$
      .pipe(takeUntilDestroyed())
      .subscribe(users => this.users = users);

    this.workspaces$
      .pipe(takeUntilDestroyed())
      .subscribe(workspaces => this.workspaces = workspaces);

    this.boards$
      .pipe(takeUntilDestroyed())
      .subscribe(boards => this.boards = boards);
  }

  changeRole(userId: number, role: string): void {
    this.store.dispatch(AdminActions.updateUserRole({ userId, role }));
  }

  suspend(userId: number): void {
    this.store.dispatch(AdminActions.suspendUser({ userId }));
  }

  reactivate(userId: number): void {
    this.store.dispatch(AdminActions.reactivateUser({ userId }));
  }

  async delete(userId: number): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Delete user permanently?',
      description: 'This action cannot be undone and will remove the user from the platform.',
      confirmText: 'Delete user',
      cancelText: 'Cancel',
      variant: 'danger'
    });
    if (!confirmed) {
      return;
    }
    this.store.dispatch(AdminActions.deleteUser({ userId }));
  }

  canManageUser(userId: number): boolean {
    return this.authService.getUserId() !== userId;
  }

  async deleteWorkspace(workspaceId: number): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Delete workspace permanently?',
      description: 'This action cannot be undone and will remove the workspace for every member.',
      confirmText: 'Delete workspace',
      cancelText: 'Cancel',
      variant: 'danger'
    });
    if (!confirmed) {
      return;
    }

    this.store.dispatch(AdminActions.deleteWorkspace({ workspaceId }));
  }

  closeBoard(boardId: number): void {
    this.store.dispatch(AdminActions.closeBoard({ boardId }));
  }

  reopenBoard(boardId: number): void {
    this.store.dispatch(AdminActions.reopenBoard({ boardId }));
  }

  async deleteBoard(boardId: number): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Delete board permanently?',
      description: 'This action cannot be undone and will permanently remove all board data.',
      confirmText: 'Delete board',
      cancelText: 'Cancel',
      variant: 'danger'
    });
    if (!confirmed) {
      return;
    }

    this.store.dispatch(AdminActions.deleteBoard({ boardId }));
  }

  getUserInitials(fullName: string | null | undefined): string {
    return (fullName || 'User')
      .split(' ')
      .map(part => part[0] ?? '')
      .join('')
      .toUpperCase()
      .slice(0, 2);
  }

  exportUsers(users: UserProfile[]): void {
    this.downloadCsv(
      'platform-users.csv',
      ['Name', 'Email', 'Role', 'Status', 'Verified', 'Created At'],
      users.map(user => [
        user.fullName,
        user.email,
        user.role,
        user.active ? 'Active' : 'Suspended',
        user.emailVerified ? 'Verified' : 'Pending',
        this.formatDateTime(user.createdAt)
      ])
    );
  }

  exportWorkspaces(workspaces: Workspace[]): void {
    this.downloadCsv(
      'platform-workspaces.csv',
      ['Workspace', 'Owner Id', 'Visibility', 'Members', 'Updated At'],
      workspaces.map(workspace => [
        workspace.name,
        String(workspace.ownerId),
        workspace.visibility,
        String(workspace.members.length),
        this.formatDateTime(workspace.updatedAt || workspace.createdAt)
      ])
    );
  }

  exportBoards(boards: Board[]): void {
    this.downloadCsv(
      'platform-boards.csv',
      ['Board', 'Workspace Id', 'Visibility', 'Status', 'Members', 'Updated At'],
      boards.map(board => [
        board.name,
        String(board.workspaceId),
        board.visibility,
        board.isClosed ? 'Closed' : 'Open',
        String(board.memberCount),
        this.formatDateTime(board.updatedAt || board.createdAt)
      ])
    );
  }

  prepareBroadcast(): void {
    if (!this.broadcastMessage.trim()) {
      this.snack.open('Enter a message before preparing the broadcast.', 'Close', { duration: 3000 });
      return;
    }

    this.snack.open(`Broadcast draft prepared for ${this.getAudienceLabel(this.broadcastAudience)}.`, 'Close', { duration: 3500 });
  }

  private buildActivityFeed(users: UserProfile[], workspaces: Workspace[], boards: Board[]) {
    return [
      ...users.map(user => ({
        type: user.active ? 'user' : 'risk',
        title: user.active ? 'User profile updated' : 'User suspension recorded',
        description: `${user.fullName} · ${user.role === 'PLATFORM_ADMIN' ? 'Platform admin' : 'Member account'}`,
        timestamp: user.updatedAt || user.createdAt
      })),
      ...workspaces.map(workspace => ({
        type: 'workspace',
        title: workspace.updatedAt ? 'Workspace updated' : 'Workspace created',
        description: `${workspace.name} · ${workspace.members.length} members · ${workspace.visibility.toLowerCase()}`,
        timestamp: workspace.updatedAt || workspace.createdAt
      })),
      ...boards.map(board => ({
        type: board.isClosed ? 'risk' : 'board',
        title: board.isClosed ? 'Board closed' : 'Board activity recorded',
        description: `${board.name} · ${board.memberCount} members · ${board.visibility.toLowerCase()}`,
        timestamp: board.updatedAt || board.createdAt
      }))
    ]
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
      .slice(0, 8);
  }

  private downloadCsv(filename: string, headers: string[], rows: string[][]): void {
    const csv = [
      headers.join(','),
      ...rows.map(row => row.map(value => `"${String(value).replace(/"/g, '""')}"`).join(','))
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  private formatDateTime(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleString() : 'N/A';
  }

  private getAudienceLabel(value: string): string {
    switch (value) {
      case 'platform-admins':
        return 'platform admins';
      case 'active-users':
        return 'active users';
      case 'workspace-owners':
        return 'workspace owners';
      default:
        return 'all users';
    }
  }
}
