import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { BoardService } from './board.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('BoardService', () => {
  let service: BoardService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...createHttpTestingProviders()]
    });
    service = TestBed.inject(BoardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('covers board retrieval endpoints', () => {
    service.getByWorkspace(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/workspace/1`).flush([]);

    service.getById(2).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/2`).flush({});

    service.getPublicView(3).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/public/3`).flush({});

    service.getByMember(4).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/member/4`).flush([]);

    service.getAnalytics(5).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/5/analytics`).flush({});
  });

  it('covers mutating and search endpoints', () => {
    service.create({} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards`).flush({});

    service.update(1, {} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1`).flush({});

    service.close(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1/close`).flush({});

    service.reopen(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1/reopen`).flush({});

    service.delete(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1`).flush('ok');

    service.addMember(1, 2, 'MEMBER').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1/members`).flush({});

    service.removeMember(1, 2).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1/members/2`).flush('ok');

    service.updateMemberRole(1, 2, 'ADMIN').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/1/members/2/role`).flush('ok');

    service.search('board').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/boards/search?keyword=board`).flush([]);
  });
});
