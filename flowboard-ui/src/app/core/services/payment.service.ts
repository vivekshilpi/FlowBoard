import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Plan {
  id: number;
  name: string;
  displayName: string;
  description: string;
  priceMonthly: number;
  priceYearly: number;
  maxWorkspaces: number;
  maxBoardsPerWorkspace: number;
  maxMembersPerWorkspace: number;
  hasAdvancedAnalytics: boolean;
  hasPrioritySupport: boolean;
  hasCustomFields: boolean;
  hasAutomation: boolean;
}

export interface Subscription {
  id: number;
  planName: string;
  planDisplayName: string;
  status: string;
  billingCycle: string;
  currentPeriodEnd: string;
  hasAdvancedAnalytics: boolean;
  hasPrioritySupport: boolean;
  hasCustomFields: boolean;
  hasAutomation: boolean;
  maxWorkspaces: number;
  maxBoardsPerWorkspace: number;
}

export interface CheckoutSession { sessionId: string; checkoutUrl: string; }
export interface CheckoutSessionStatus {
  status: string;
  planName: string | null;
  billingCycle: string | null;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/payments`;

  readonly currentPlan = signal<Subscription | null>(null);
  readonly isPro = signal(false);
  readonly isBusiness = signal(false);

  getPlans(): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${this.base}/plans`);
  }

  getSubscription(): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.base}/subscription`).pipe(
      tap(sub => {
        this.syncCurrentPlan(sub);
      })
    );
  }

  createCheckout(planId: number, billingCycle: 'MONTHLY' | 'YEARLY'): Observable<CheckoutSession> {
    return this.http.post<CheckoutSession>(`${this.base}/checkout`, { planId, billingCycle });
  }

  confirmCheckoutSession(sessionId: string): Observable<CheckoutSessionStatus> {
    return this.http.get<CheckoutSessionStatus>(`${this.base}/checkout/session/${sessionId}`).pipe(
      tap(status => {
        if (status.status === 'ACTIVE') {
          this.getSubscription().subscribe({ error: () => {} });
        }
      })
    );
  }

  cancelSubscription(): Observable<string> {
    return this.http.post(`${this.base}/cancel`, {}, { responseType: 'text' }).pipe(
      tap(() => {
        this.currentPlan.set(null);
        this.isPro.set(false);
        this.isBusiness.set(false);
      })
    );
  }

  hasFeature(feature: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.base}/feature/${feature}`);
  }

  getPaymentHistory(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/history`);
  }

  isPaidPlan(): boolean {
    const plan = this.currentPlan();
    return plan !== null && plan.planName !== 'FREE' && plan.status === 'ACTIVE';
  }

  canCreateWorkspace(currentCount: number): boolean {
    const plan = this.currentPlan();
    if (!plan) return currentCount < 3;
    return plan.maxWorkspaces === -1 || currentCount < plan.maxWorkspaces;
  }

  private syncCurrentPlan(sub: Subscription): void {
    const isActive = sub.status === 'ACTIVE';
    this.currentPlan.set(isActive ? sub : null);
    this.isPro.set(isActive && sub.planName === 'PRO');
    this.isBusiness.set(isActive && sub.planName === 'BUSINESS');
  }
}
