import { Component, inject, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { PaymentService } from '../../../core/services/payment.service';
import { Workspace } from '../../../core/models/workspace.model';
import { UserProfile } from '../../../core/models/user.model';
import { ColorPickerComponent } from '../../../shared/components/color-picker/color-picker.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatMenuModule, MatDividerModule,
    MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule, ColorPickerComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private fb               = inject(FormBuilder);
  private auth             = inject(AuthService);
  private workspaceService = inject(WorkspaceService);
  readonly paymentService  = inject(PaymentService);
  public  router           = inject(Router);
  private snack            = inject(MatSnackBar);
  private dialog           = inject(DialogService);

  workspaces: Workspace[] = [];
  currentUser: UserProfile | null = null;
  loading = true;
  creating = false;
  showCreateForm = false;
  showLimitBanner = false;

  get canCreate(): boolean {
    return this.paymentService.canCreateWorkspace(this.workspaces.length);
  }

  get isPaidUser(): boolean { return this.paymentService.isPaidPlan(); }

  get planLabel(): string {
    return (this.paymentService.currentPlan()?.planDisplayName ?? 'Free') + ' Plan';
  }

  get planName(): string {
    return this.paymentService.currentPlan()?.planDisplayName ?? 'Free';
  }

  createForm = this.fb.group({
    name:        ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
    visibility:  ['PRIVATE', Validators.required]
  });

  get name() { return this.createForm.get('name')!; }

  ngOnInit() {
    this.paymentService.getSubscription().subscribe();
    this.auth.getProfile().subscribe({
      next: u => { this.currentUser = u; this.loadWorkspaces(u.id); },
      error: () => this.auth.logout()
    });
  }

  loadWorkspaces(userId: number) {
    this.loading = true;
    this.workspaceService.getByMember(userId).subscribe({
      next: ws => { this.workspaces = ws; this.loading = false; },
      error: () => { this.loading = false; this.snack.open('Failed to load workspaces', 'Close', { duration: 3000 }); }
    });
  }

  tryCreateWorkspace() {
    if (!this.canCreate) {
      this.showLimitBanner = true;
      setTimeout(() => this.showLimitBanner = false, 6000);
      return;
    }
    this.showCreateForm = true;
  }

  createWorkspace() {
    if (this.createForm.invalid) return;
    this.creating = true;
    this.workspaceService.create(this.createForm.value as any).subscribe({
      next: ws => {
        this.workspaces.unshift(ws);
        this.creating = false; this.showCreateForm = false;
        this.createForm.reset({ visibility: 'PRIVATE' });
        this.snack.open('Workspace created!', 'Close', { duration: 3000 });
      },
      error: err => {
        this.creating = false;
        this.snack.open(err.error?.message ?? 'Create failed', 'Close', { duration: 4000 });
      }
    });
  }

  openWorkspace(id: number) { this.router.navigate(['/workspace', id]); }

  async deleteWorkspace(id: number, e: Event) {
    e.stopPropagation();
    const confirmed = await this.dialog.confirm({
      title: 'Delete workspace?',
      description: 'This workspace will be removed from your dashboard immediately.',
      confirmText: 'Delete workspace',
      cancelText: 'Keep workspace',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.workspaceService.delete(id)),
      errorMessage: 'Failed to delete workspace'
    });
    if (!confirmed) return;
    this.workspaces = this.workspaces.filter(w => w.id !== id);
    this.snack.open('Deleted', 'Close', { duration: 2000 });
  }

  getInitials(name: string) { return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2); }
  logout() { this.auth.logout(); }
  getWorkspaceColor(id: number): string | null { return localStorage.getItem(`ws-color-${id}`); }
  setWorkspaceColor(id: number, color: string | null) {
    color ? localStorage.setItem(`ws-color-${id}`, color) : localStorage.removeItem(`ws-color-${id}`);
  }
}
