import { Component, inject } from '@angular/core';
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
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatCardModule, MatProgressSpinnerModule, MatIconModule
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {

  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);

  loading        = false;
  errorMessage   = '';
  successMessage = '';

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  get email() { return this.form.get('email')!; }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    this.auth.forgotPassword(this.email.value!).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = `OTP sent to ${this.email.value}`;
        localStorage.setItem('resetEmail', this.email.value!);
        setTimeout(() =>
          this.router.navigate(['/reset-password']), 1500);
      },
      error: err => {
        this.loading = false;
        this.errorMessage = err.error?.message ?? 'Failed to send OTP.';
      }
    });
  }
}