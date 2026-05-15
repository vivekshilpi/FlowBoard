import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule, FormBuilder,
  FormGroup, Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatCardModule
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {

  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);
  private route  = inject(ActivatedRoute);

  form: FormGroup = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  loading       = false;
  hidePassword  = true;
  errorMessage  = '';

  get email()    { return this.form.get('email')!; }
  get password() { return this.form.get('password')!; }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    this.auth.login(this.form.value).subscribe({
      next: () => {
        this.auth.getProfile().subscribe({
          next: () => this.navigateAfterLogin(),
          error: () => this.navigateAfterLogin()
        });
      },
      error: err => {
        this.loading = false;
        if (err.status === 403) {
          this.errorMessage = this.auth.getErrorMessage(
            err,
            'Email not verified. Check your inbox.'
          );
          localStorage.setItem('pendingEmail', this.email.value);
          setTimeout(() =>
            this.router.navigate(['/verify-email']), 2000);
        } else {
          this.errorMessage = this.auth.getErrorMessage(
            err,
            'Login failed. Try again.'
          );
        }
      }
    });
  }

  goToForgotPassword() {
    this.router.navigate(['/forgot-password']);
  }

  private navigateAfterLogin(): void {
    const redirectUrl = this.route.snapshot.queryParamMap.get('redirectUrl');
    if (redirectUrl) {
      this.router.navigateByUrl(redirectUrl);
      return;
    }
    this.router.navigate(['/dashboard']);
  }
}
