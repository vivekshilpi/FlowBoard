import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-oauth2-callback',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule],
  template: `
    <div style="display:flex;flex-direction:column;align-items:center;
                justify-content:center;min-height:100vh;gap:16px">
      <mat-spinner diameter="48"></mat-spinner>
      <p style="color:#64748b;font-size:14px">Completing login...</p>
    </div>
  `
})
export class Oauth2CallbackComponent implements OnInit {

  private route  = inject(ActivatedRoute);
  private router = inject(Router);
  private auth   = inject(AuthService);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const token  = params['token'];
      const userId = params['userId'];
      const role   = params['role'];

      if (token) {
        // Store exactly the same way as normal login
        localStorage.setItem('token',    token);
        localStorage.setItem('userId',   userId);
        localStorage.setItem('userRole', role);

        // Fetch full profile to populate name/email in localStorage
        this.auth.getProfile().subscribe({
          next: () => this.navigateAfterLogin(params['redirectUrl']),
          error: () => this.navigateAfterLogin(params['redirectUrl'])
        });
      } else {
        this.router.navigate(['/login'],
          { queryParams: { error: 'oauth_failed' }});
      }
    });
  }

  private navigateAfterLogin(redirectUrl?: string): void {
    if (redirectUrl) {
      this.router.navigateByUrl(redirectUrl);
      return;
    }
    this.router.navigate(['/dashboard']);
  }
}
