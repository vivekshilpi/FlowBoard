import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { PaymentService, Subscription } from './payment.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;

  const activeSubscription: Subscription = {
    id: 1,
    planName: 'PRO',
    planDisplayName: 'Pro',
    status: 'ACTIVE',
    billingCycle: 'MONTHLY',
    currentPeriodEnd: '2026-06-01',
    hasAdvancedAnalytics: true,
    hasPrioritySupport: true,
    hasCustomFields: true,
    hasAutomation: true,
    maxWorkspaces: 10,
    maxBoardsPerWorkspace: 25
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...createHttpTestingProviders()]
    });
    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads plans', () => {
    let response: unknown;
    service.getPlans().subscribe(value => response = value);

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/plans`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'FREE' }]);

    expect(response).toEqual([{ id: 1, name: 'FREE' }]);
  });

  it('syncs plan signals from getSubscription', () => {
    service.getSubscription().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/subscription`);
    req.flush(activeSubscription);

    expect(service.currentPlan()).toEqual(activeSubscription);
    expect(service.isPro()).toBeTrue();
    expect(service.isBusiness()).toBeFalse();
    expect(service.isPaidPlan()).toBeTrue();
  });

  it('creates checkout and refreshes subscription when session becomes active', () => {
    service.confirmCheckoutSession('sess_1').subscribe();

    const sessionReq = httpMock.expectOne(`${environment.apiUrl}/payments/checkout/session/sess_1`);
    sessionReq.flush({
      status: 'ACTIVE',
      planName: 'PRO',
      billingCycle: 'MONTHLY',
      message: 'ok'
    });

    const subscriptionReq = httpMock.expectOne(`${environment.apiUrl}/payments/subscription`);
    subscriptionReq.flush(activeSubscription);

    expect(service.currentPlan()?.planName).toBe('PRO');
  });

  it('does not refresh subscription for inactive checkout status', () => {
    service.confirmCheckoutSession('sess_2').subscribe();

    const sessionReq = httpMock.expectOne(`${environment.apiUrl}/payments/checkout/session/sess_2`);
    sessionReq.flush({
      status: 'PENDING',
      planName: null,
      billingCycle: null,
      message: 'pending'
    });

    httpMock.expectNone(`${environment.apiUrl}/payments/subscription`);
    expect(service.currentPlan()).toBeNull();
  });

  it('cancels subscription and clears local signals', () => {
    (service as any).currentPlan.set(activeSubscription);
    service.isPro.set(true);

    let response = '';
    service.cancelSubscription().subscribe(value => response = value);

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush('cancelled');

    expect(response).toBe('cancelled');
    expect(service.currentPlan()).toBeNull();
    expect(service.isPro()).toBeFalse();
    expect(service.isBusiness()).toBeFalse();
  });

  it('supports feature/history helpers and workspace limits', () => {
    let feature = false;
    let history: unknown[] = [];
    service.hasFeature('analytics').subscribe(value => feature = value);
    service.getPaymentHistory().subscribe(value => history = value);

    httpMock.expectOne(`${environment.apiUrl}/payments/feature/analytics`).flush(true);
    httpMock.expectOne(`${environment.apiUrl}/payments/history`).flush([{ id: 1 }]);

    expect(feature).toBeTrue();
    expect(history).toEqual([{ id: 1 }]);

    expect(service.canCreateWorkspace(2)).toBeTrue();
    expect(service.canCreateWorkspace(3)).toBeFalse();

    (service as any).currentPlan.set({ ...activeSubscription, maxWorkspaces: -1 });
    expect(service.canCreateWorkspace(999)).toBeTrue();

    (service as any).currentPlan.set({ ...activeSubscription, maxWorkspaces: 5 });
    expect(service.canCreateWorkspace(4)).toBeTrue();
    expect(service.canCreateWorkspace(5)).toBeFalse();
  });

  it('creates checkout requests', () => {
    service.createCheckout(2, 'YEARLY').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/checkout`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ planId: 2, billingCycle: 'YEARLY' });
    req.flush({ sessionId: 'sess', checkoutUrl: 'https://checkout.test' });
  });
});
