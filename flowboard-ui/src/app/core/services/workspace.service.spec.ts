import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { WorkspaceService } from './workspace.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('WorkspaceService', () => {
  let service: WorkspaceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...createHttpTestingProviders()]
    });
    service = TestBed.inject(WorkspaceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('covers workspace retrieval endpoints', () => {
    service.getByMember(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/member/1`).flush([]);

    service.getByOwner(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/owner/1`).flush([]);

    service.getPublic().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/public`).flush([]);

    service.getById(2).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/2`).flush({});

    service.getMembers(3).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/3/members`).flush([]);

    service.getInvitationDetails(4).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/invite/4`).flush({});

    service.getPendingInvitations(5).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/5/invitations`).flush([]);
  });

  it('covers workspace mutation and invitation endpoints', () => {
    service.create({} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces`).flush({});

    service.update(1, {} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1`).flush({});

    service.delete(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1`).flush('ok');

    service.addMember(1, 2, 'MEMBER').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1/members`).flush({});

    service.removeMember(1, 2).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1/members/2`).flush('ok');

    service.updateMemberRole(1, 2, 'ADMIN').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1/members/2/role`).flush('ok');

    service.inviteMember(1, 'user@example.com', 'MEMBER').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1/invite`).flush('ok');

    service.acceptInvitation('token').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/invite/accept?token=token`).flush('ok');

    service.acceptInvitationById(9).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/invite/9/accept`).flush({});

    service.rejectInvitation(9).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/invite/9/reject`).flush({});

    service.revokeInvitation(1, 9).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/1/invitations/9`).flush('ok');

    service.search('team').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/workspaces/search?keyword=team`).flush([]);
  });
});
