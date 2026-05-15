import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-payment-cancel',
  standalone: true,
  imports: [RouterModule, MatButtonModule, MatIconModule],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-slate-900 to-indigo-950 flex items-center justify-center px-4">
      <div class="bg-white rounded-3xl p-8 sm:p-12 max-w-md w-full text-center shadow-2xl">
        <div class="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-5">
          <mat-icon class="text-amber-500" style="font-size:36px;width:36px;height:36px;">info</mat-icon>
        </div>
        <h1 class="text-2xl font-black text-slate-800 mb-2">Payment Cancelled</h1>
        <p class="text-slate-500 text-sm mb-8">No worries! Your free plan is still active. You can upgrade anytime.</p>
        <div class="space-y-3">
          <button mat-flat-button color="primary" routerLink="/pricing" class="w-full rounded-xl font-bold py-3">View Plans</button>
          <button mat-button routerLink="/dashboard" class="w-full text-slate-500 text-sm">Back to Dashboard</button>
        </div>
      </div>
    </div>
  `
})
export class PaymentCancelComponent {}