import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatCardModule, MatProgressSpinnerModule, MatIconModule
  ],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss'
})
export class VerifyEmailComponent implements OnInit {

  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);

  email          = '';
  loading        = false;
  resending      = false;
  errorMessage   = '';
  successMessage = '';

  form = this.fb.group({
    otp: ['', [
      Validators.required,
      Validators.pattern('^[0-9]{6}$')
    ]]
  });

  get otp() { return this.form.get('otp')!; }

  ngOnInit(): void {
    this.email = localStorage.getItem('pendingEmail') ?? '';
    if (!this.email) this.router.navigate(['/register']);
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    this.auth.verifyEmail(this.email, this.otp.value!).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Verified! Redirecting to login...';
        localStorage.removeItem('pendingEmail');
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: err => {
        this.loading = false;
        this.errorMessage = err.error?.message ?? 'Invalid OTP.';
      }
    });
  }

  resend(): void {
    this.resending = true;
    this.auth.resendVerification(this.email).subscribe({
      next: () => {
        this.resending = false;
        this.successMessage = `New OTP sent to ${this.email}`;
      },
      error: () => {
        this.resending = false;
        this.errorMessage = 'Failed to resend OTP.';
      }
    });
  }
}