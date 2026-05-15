import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { PaymentService } from '../../../core/services/payment.service';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { MatSnackBar } from '@angular/material/snack-bar';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let workspaceService: jasmine.SpyObj<WorkspaceService>;
  let paymentService: jasmine.SpyObj<PaymentService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;
  let snack: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [...createComponentTestingProviders()]
    }).compileComponents();

    component = TestBed.createComponent(DashboardComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    workspaceService = TestBed.inject(WorkspaceService) as jasmine.SpyObj<WorkspaceService>;
    paymentService = TestBed.inject(PaymentService) as jasmine.SpyObj<PaymentService>;
    dialog = TestBed.inject(DialogService) as jasmine.SpyObj<DialogService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    snack = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;

    spyOn(paymentService, 'getSubscription').and.returnValue(of({} as any));
    spyOn(paymentService, 'canCreateWorkspace').and.returnValue(true);
    spyOn(paymentService, 'isPaidPlan').and.returnValue(true);
    paymentService.currentPlan.set({ planDisplayName: 'Pro', planName: 'PRO', status: 'ACTIVE', maxWorkspaces: 10 } as any);
    spyOn(auth, 'getProfile').and.returnValue(of({ id: 9, fullName: 'Flow User' } as any));
    spyOn(auth, 'logout');
    spyOn(workspaceService, 'getByMember').and.returnValue(of([]));
    spyOn(workspaceService, 'create').and.returnValue(of({ id: 4, name: 'Created' } as any));
    spyOn(workspaceService, 'delete').and.returnValue(of('deleted' as any));
    spyOn(dialog as any, 'confirm').and.callFake(async (options: any) => {
      if (options?.onConfirm) {
        await options.onConfirm();
      }
      return true;
    });
    spyOn(router, 'navigate');
    spyOn(snack, 'open');
    localStorage.removeItem('ws-color-7');
  });

  it('loads profile and workspaces on init and exposes payment derived values', () => {
    component.ngOnInit();

    expect(paymentService.getSubscription).toHaveBeenCalled();
    expect(auth.getProfile).toHaveBeenCalled();
    expect(workspaceService.getByMember).toHaveBeenCalledWith(9);
    expect(component.planLabel).toBe('Pro Plan');
    expect(component.planName).toBe('Pro');
    expect(component.isPaidUser).toBeTrue();
    expect(component.canCreate).toBeTrue();
  });

  it('shows the limit banner when workspace creation is blocked', fakeAsync(() => {
    paymentService.canCreateWorkspace.and.returnValue(false);

    component.tryCreateWorkspace();
    expect(component.showLimitBanner).toBeTrue();

    tick(6000);
    expect(component.showLimitBanner).toBeFalse();
  }));

  it('creates and deletes workspaces and handles navigation helpers', async () => {
    component.createForm.setValue({
      name: 'Workspace',
      description: 'Desc',
      visibility: 'PRIVATE'
    });

    component.createWorkspace();
    expect(workspaceService.create).toHaveBeenCalled();
    expect(component.workspaces[0].id).toBe(4);

    component.openWorkspace(7);
    expect(router.navigate).toHaveBeenCalledWith(['/workspace', 7]);

    component.setWorkspaceColor(7, '#123456');
    expect(component.getWorkspaceColor(7)).toBe('#123456');
    component.setWorkspaceColor(7, null);
    expect(component.getWorkspaceColor(7)).toBeNull();

    component.workspaces = [{ id: 4, name: 'Created' } as any];
    await component.deleteWorkspace(4, new Event('click'));
    expect(workspaceService.delete).toHaveBeenCalledWith(4);
    expect(component.workspaces).toEqual([]);
  });

  it('handles failures and logout paths', () => {
    auth.getProfile.and.returnValue(throwError(() => new Error('profile fail')));
    component.ngOnInit();
    expect(auth.logout).toHaveBeenCalled();

    workspaceService.getByMember.and.returnValue(throwError(() => new Error('ws fail')));
    component.loadWorkspaces(9);
    expect(component.loading).toBeFalse();

    workspaceService.create.and.returnValue(throwError(() => ({
      error: { message: 'Create failed loudly' }
    })));
    component.createForm.setValue({
      name: 'Workspace',
      description: 'Desc',
      visibility: 'PRIVATE'
    });
    component.createWorkspace();
    expect(component.creating).toBeFalse();

    component.logout();
    expect(auth.logout).toHaveBeenCalledTimes(2);
  });

  it('returns workspace initials', () => {
    expect(component.getInitials('Flow Board')).toBe('FB');
  });
});
