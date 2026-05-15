import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Store } from '@ngrx/store';
import { finalize, of, switchMap } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ProfileActivityLog, UserProfile } from '../../../core/models/user.model';
import { PaymentService, Subscription } from '../../../core/services/payment.service';
import * as AuthActions from '../../../store/auth/auth.actions';

@Component({
  selector: 'app-profile-view',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatDividerModule,
    MatProgressSpinnerModule, MatSnackBarModule
  ],
  templateUrl: './profile-view.component.html',
  styleUrl: './profile-view.component.scss'
})
export class ProfileViewComponent implements OnInit {
  readonly activityLimit = 20;
  readonly totalPages = 2;

  private fb    = inject(FormBuilder);
  private auth  = inject(AuthService);
  private snack = inject(MatSnackBar);
  private store = inject(Store);
  readonly paymentService = inject(PaymentService);

  user: UserProfile | null = null;
  subscription: Subscription | null = null;
  loading        = true;
  savingProfile  = false;
  savingPassword = false;
  hideOld = true;
  hideNew = true;
  error: string | null = null;
  avatarLoadFailed = false;
  originalProfileData: any = null;
  selectedAvatarFile: File | null = null;
  avatarPreviewUrl: string | null = null;
  readonly maxAvatarSizeBytes = 2 * 1024 * 1024;
  activityLog: ProfileActivityLog[] = [];
  activityLoading = false;
  currentPage = 1;

  profileForm = this.fb.group({
    fullname:  ['', [Validators.required, Validators.minLength(1)]],
    username:  ['', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(30),
      Validators.pattern(/^[A-Za-z0-9._-]+$/)
    ]],
    bio:       ['', [Validators.maxLength(280)]]
  });

  passwordForm = this.fb.group({
    oldPassword: ['', Validators.required],
    newPassword: ['', [
      Validators.required,
      Validators.minLength(8),
      Validators.maxLength(128),
      Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).+$/)
    ]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordsMatchValidator() });

  ngOnInit(): void {
    this.loadProfile();
    this.loadSubscription();
    this.loadProfileActivity();
  }

  saveProfile(): void {
    if (this.profileForm.invalid || (!this.hasProfileChanges() && !this.selectedAvatarFile)) {
      console.warn('Profile form is invalid', this.profileForm.errors);
      this.profileForm.markAllAsTouched();
      return;
    }
    this.savingProfile = true;

    const profilePayload = this.profileForm.getRawValue();

    const avatarUpload$ = this.selectedAvatarFile
      ? this.auth.uploadAvatar(this.selectedAvatarFile)
      : of(this.user);

    avatarUpload$.pipe(
      switchMap(() => this.auth.updateProfile(profilePayload as any)),
      finalize(() => {
        this.savingProfile = false;
      })
    ).subscribe({
      next: (user) => {
        this.applyUser(user);
        this.selectedAvatarFile = null;
        this.loadProfileActivity();
        this.snack.open('Profile updated!', 'Close', { duration: 3000 });
      },
      error: err => {
        console.error('Profile update failed:', err);
        this.snack.open(
          this.auth.getErrorMessage(err, 'Update failed'),
          'Close', { duration: 4000 });
      }
    });
  }

  private loadProfile(): void {
    this.loading = true;
    this.avatarLoadFailed = false;
    this.auth.getProfile().subscribe({
      next: user => {
        this.user = user;
        this.loading = false;
        this.error = null;
        this.avatarLoadFailed = false;
        this.applyUser(user);
      },
      error: (err) => {
        console.error('Failed to load profile:', err);
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load profile';
        this.snack.open(this.error ?? 'Failed to load profile', 'Close', { duration: 5000 });
      }
    });
  }

  hasProfileChanges(): boolean {
    if (!this.originalProfileData) {
      return false;
    }
    return JSON.stringify(this.originalProfileData) !== JSON.stringify(this.profileForm.value);
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.savingPassword = true;

    const { oldPassword, newPassword } = this.passwordForm.getRawValue();
    this.auth.changePassword({ oldPassword: oldPassword ?? '', newPassword: newPassword ?? '' }).pipe(
      finalize(() => {
        this.savingPassword = false;
      })
    ).subscribe({
      next: () => {
        this.passwordForm.reset();
        this.passwordForm.setErrors(null);
        this.loadProfileActivity();
        this.snack.open('Password changed!', 'Close',
          { duration: 3000 });
      },
      error: err => {
        this.snack.open(
          this.auth.getErrorMessage(err, 'Change failed'),
          'Close', { duration: 4000 });
      }
    });
  }

  onAvatarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      this.snack.open('Please choose a JPG or PNG image.', 'Close', { duration: 4000 });
      input.value = '';
      return;
    }

    if (file.size > this.maxAvatarSizeBytes) {
      this.snack.open('Avatar image must be 2 MB or smaller.', 'Close', { duration: 4000 });
      input.value = '';
      return;
    }

    this.selectedAvatarFile = file;
    const reader = new FileReader();
    reader.onload = () => {
      this.avatarPreviewUrl = typeof reader.result === 'string' ? reader.result : null;
    };
    reader.readAsDataURL(file);
  }

  clearSelectedAvatar(): void {
    this.selectedAvatarFile = null;
    this.avatarPreviewUrl = null;
  }

  setPage(page: number): void {
    if (page < 1 || page > this.totalPages) {
      return;
    }
    this.currentPage = page;
  }

  nextPage(): void {
    this.setPage(this.currentPage + 1);
  }

  previousPage(): void {
    this.setPage(this.currentPage - 1);
  }

  getInitials(): string {
    return (this.user?.fullName ?? 'U')
      .split(' ').map(n => n[0])
      .join('').toUpperCase().slice(0, 2);
  }

  onAvatarError(event: Event): void {
    console.warn('Avatar image failed to load');
    this.avatarLoadFailed = true;
  }

  onAvatarLoad(): void {
    console.log('Avatar image loaded successfully');
    this.avatarLoadFailed = false;
  }

  getAvatarUrl(): string {
    const source = this.avatarPreviewUrl ?? this.user?.avatarUrl;
    if (!source || this.avatarLoadFailed) {
      return '';
    }

    if (source.startsWith('data:')) {
      return source;
    }

    return this.auth.resolveAssetUrl(source);
  }

  getHeaderAvatarUrl(): string {
    const source = this.avatarPreviewUrl ?? this.user?.avatarUrl;
    if (!source) {
      return '';
    }
    return this.auth.resolveAssetUrl(source);
  }

  private applyUser(user: UserProfile): void {
    this.user = user;
    this.avatarPreviewUrl = null;
    this.store.dispatch(AuthActions.getProfileSuccess({ user }));
    this.profileForm.patchValue({
      fullname: user.fullName,
      username: user.username,
      bio: user.bio ?? ''
    });
    this.originalProfileData = this.profileForm.getRawValue();
    this.profileForm.markAsPristine();
  }

  private loadProfileActivity(): void {
    this.activityLoading = true;
    this.auth.getProfileActivity().pipe(
      finalize(() => {
        this.activityLoading = false;
      })
    ).subscribe({
      next: activity => {
        this.activityLog = activity.slice(0, this.activityLimit);
      },
      error: () => {
        this.activityLog = [];
      }
    });
  }

  private loadSubscription(): void {
    this.paymentService.getSubscription().subscribe({
      next: subscription => {
        this.subscription = subscription;
      },
      error: () => {
        this.subscription = null;
      }
    });
  }

  private passwordsMatchValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const currentPassword = control.get('oldPassword')?.value;
      const newPassword = control.get('newPassword')?.value;
      const confirmPassword = control.get('confirmPassword')?.value;

      if (!newPassword || !confirmPassword) {
        return null;
      }

      if (newPassword !== confirmPassword) {
        return { passwordMismatch: true };
      }

      if (currentPassword && currentPassword === newPassword) {
        return { sameAsCurrent: true };
      }

      return null;
    };
  }
}
