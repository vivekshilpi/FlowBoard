import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import {
  InvitationActionResponse,
  InvitationDetails
} from '../../../core/models/invitation.model';
import { WorkspaceService } from '../../../core/services/workspace.service';

@Component({
  selector: 'app-invitation-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './invitation-detail.component.html',
  styleUrl: './invitation-detail.component.scss'
})
export class InvitationDetailComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private workspaceService = inject(WorkspaceService);
  private snack = inject(MatSnackBar);
  private destroy$ = new Subject<void>();

  invitation: InvitationDetails | null = null;
  loading = true;
  accepting = false;
  rejecting = false;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const id = Number(params.get('id'));
      if (!id) {
        this.loading = false;
        this.snack.open('Invitation not found', 'Close', { duration: 3000 });
        this.router.navigate(['/notifications']);
        return;
      }
      this.loadInvitation(id);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  accept(): void {
    if (!this.invitation || this.accepting || !this.canRespond()) {
      return;
    }

    this.accepting = true;
    this.workspaceService.acceptInvitationById(this.invitation.id).subscribe({
      next: response => {
        this.accepting = false;
        this.handleSuccess(response, true);
      },
      error: err => {
        this.accepting = false;
        this.snack.open(err.error?.message ?? 'Failed to accept invitation', 'Close', {
          duration: 4000
        });
        this.reloadCurrent();
      }
    });
  }

  reject(): void {
    if (!this.invitation || this.rejecting || !this.canRespond()) {
      return;
    }

    this.rejecting = true;
    this.workspaceService.rejectInvitation(this.invitation.id).subscribe({
      next: response => {
        this.rejecting = false;
        this.handleSuccess(response, false);
      },
      error: err => {
        this.rejecting = false;
        this.snack.open(err.error?.message ?? 'Failed to ignore invitation', 'Close', {
          duration: 4000
        });
        this.reloadCurrent();
      }
    });
  }

  canRespond(): boolean {
    return this.invitation?.status === 'PENDING';
  }

  goBack(): void {
    this.router.navigate(['/notifications']);
  }

  private loadInvitation(id: number): void {
    this.loading = true;
    this.workspaceService.getInvitationDetails(id).subscribe({
      next: invitation => {
        this.invitation = invitation;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.snack.open(err.error?.message ?? 'Failed to load invitation', 'Close', {
          duration: 4000
        });
        this.router.navigate(['/notifications']);
      }
    });
  }

  private reloadCurrent(): void {
    if (this.invitation) {
      this.loadInvitation(this.invitation.id);
    }
  }

  private handleSuccess(response: InvitationActionResponse, accepted: boolean): void {
    this.snack.open(response.message, 'Close', { duration: 3500 });
    this.router.navigate(accepted
      ? ['/workspace', response.workspaceId]
      : ['/dashboard']);
  }
}
