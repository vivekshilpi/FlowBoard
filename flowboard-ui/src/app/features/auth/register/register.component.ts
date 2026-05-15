import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule, FormBuilder, Validators,
  AbstractControl, FormGroup
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
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatCardModule, MatProgressSpinnerModule
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {

  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);

  hidePassword = true;
  loading      = false;
  errorMessage = '';
  successMessage = '';

  form: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    email:    ['', [Validators.required, Validators.email]],
    username: ['', [
      Validators.required, Validators.minLength(3),
      Validators.maxLength(30),
      Validators.pattern('^[a-zA-Z0-9_]+$')
    ]],
    password:        ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatch });

  passwordMatch(g: AbstractControl) {
    return g.get('password')?.value === g.get('confirmPassword')?.value
      ? null : { mismatch: true };
  }

  get fullName() { return this.form.get('fullName')!; }
  get email()    { return this.form.get('email')!; }
  get username() { return this.form.get('username')!; }
  get password() { return this.form.get('password')!; }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    const { confirmPassword: _, ...data } = this.form.value;

    this.auth.register(data).subscribe({
      next: res => {
        this.loading = false;
        this.successMessage = res.message;
        this.errorMessage = '';
        localStorage.setItem('pendingEmail', data.email);
        setTimeout(() =>
          this.router.navigate(['/verify-email']), 1500);
      },
      error: err => {
        this.loading = false;
        this.errorMessage = this.auth.getErrorMessage(
          err,
          'Registration failed.'
        );
      }
    });
  }
}
