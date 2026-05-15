import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule, FormBuilder,
  Validators, AbstractControl
} from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatCardModule, MatProgressSpinnerModule
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent implements OnInit {

  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);

  email          = '';
  loading        = false;
  hidePassword   = true;
  errorMessage   = '';
  successMessage = '';

  form = this.fb.group({
    otp:             ['', [Validators.required,
                           Validators.pattern('^[0-9]{6}$')]],
    newPassword:     ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatch });

  passwordMatch(g: AbstractControl) {
    return g.get('newPassword')?.value ===
           g.get('confirmPassword')?.value
      ? null : { mismatch: true };
  }

  get otp()             { return this.form.get('otp')!; }
  get newPassword()     { return this.form.get('newPassword')!; }
  get confirmPassword() { return this.form.get('confirmPassword')!; }

  ngOnInit(): void {
    this.email = localStorage.getItem('resetEmail') ?? '';
    if (!this.email) this.router.navigate(['/forgot-password']);
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    this.auth.resetPassword(
      this.email, this.otp.value!, this.newPassword.value!
    ).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Password reset! Redirecting...';
        localStorage.removeItem('resetEmail');
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: err => {
        this.loading = false;
        this.errorMessage = err.error?.message ?? 'Reset failed.';
      }
    });
  }
}