import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PaymentService } from '../../../core/services/payment.service';

@Component({
  selector: 'app-payment-success',
  standalone: true,
  imports: [CommonModule, RouterModule, MatButtonModule, MatIconModule],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-slate-900 to-indigo-950 flex items-center justify-center px-4">
      <div class="bg-white rounded-3xl p-8 sm:p-12 max-w-md w-full text-center shadow-2xl">
        <div class="w-16 h-16 sm:w-20 sm:h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-5">
          <mat-icon class="text-green-500" style="font-size:36px;width:36px;height:36px;">check_circle</mat-icon>
        </div>
        <h1 class="text-2xl font-black text-slate-800 mb-2">Payment Successful!</h1>
        <p class="text-slate-500 text-sm mb-1">{{ statusMessage() }}</p>
        <p class="text-slate-500 text-sm mb-8">{{ welcomeMessage() }}</p>
        <div class="space-y-3">
          <button mat-flat-button color="primary" routerLink="/dashboard" class="w-full rounded-xl font-bold py-3">
            Go to Dashboard
          </button>
          <button mat-button routerLink="/pricing" class="w-full text-slate-500 text-sm">View subscription details</button>
        </div>
        <p class="text-xs text-slate-400 mt-6">Redirecting to dashboard in {{ countdown }}s...</p>
      </div>
    </div>
  `
})
export class PaymentSuccessComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private paymentService = inject(PaymentService);

  countdown = 5;
  statusMessage = signal('Finalizing your subscription...');
  welcomeMessage = signal('We are updating your plan details now.');

  ngOnInit() {
    const sessionId = this.route.snapshot.queryParamMap.get('session_id');

    if (sessionId) {
      this.paymentService.confirmCheckoutSession(sessionId).subscribe({
        next: result => {
          if (result.status === 'ACTIVE') {
            const currentPlan = this.paymentService.currentPlan();
            const planDisplayName = currentPlan?.planDisplayName
              ?? this.formatPlanName(result.planName);
            this.statusMessage.set('Your subscription has been activated.');
            this.welcomeMessage.set(`Welcome to FlowBoard ${planDisplayName}!`);
            return;
          }

          this.statusMessage.set(result.message || 'Your payment was received and is still being finalized.');
          this.welcomeMessage.set('We will keep your plan details in sync shortly.');
        },
        error: () => {
          this.paymentService.getSubscription().subscribe({ error: () => {} });
          this.statusMessage.set('Your payment was received. We could not verify the session yet.');
          this.welcomeMessage.set('Please open Pricing once if your plan details do not refresh automatically.');
        }
      });
    } else {
      this.paymentService.getSubscription().subscribe({ error: () => {} });
      this.statusMessage.set('Your payment was received.');
      this.welcomeMessage.set('We are refreshing your account details now.');
    }

    const t = setInterval(() => {
      this.countdown--;
      if (this.countdown <= 0) { clearInterval(t); this.router.navigate(['/dashboard']); }
    }, 1000);
  }

  private formatPlanName(planName: string | null): string {
    if (!planName) {
      return 'Pro';
    }
    return planName.charAt(0) + planName.slice(1).toLowerCase();
  }
}
