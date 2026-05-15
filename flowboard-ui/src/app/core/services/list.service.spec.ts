import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { ListService } from './list.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('ListService', () => {
  let service: ListService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...createHttpTestingProviders()]
    });
    service = TestBed.inject(ListService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('covers board retrieval endpoints', () => {
    service.getByBoard(7).subscribe();
    service.getArchived(7).subscribe();

    const activeReq = httpMock.expectOne(`${environment.apiUrl}/lists/board/7`);
    expect(activeReq.request.method).toBe('GET');
    activeReq.flush([]);

    const archivedReq = httpMock.expectOne(`${environment.apiUrl}/lists/board/7/archived`);
    expect(archivedReq.request.method).toBe('GET');
    archivedReq.flush([]);
  });

  it('covers create update reorder archive and unarchive endpoints', () => {
    service.create({ boardId: 2, name: 'Todo', color: '#fff' } as any).subscribe();
    service.update(4, { name: 'Doing', color: '#000' } as any).subscribe();
    service.reorder({ boardId: 2, orderedListIds: [4, 5] } as any).subscribe();
    service.archive(4).subscribe();
    service.unarchive(4).subscribe();

    const createReq = httpMock.expectOne(`${environment.apiUrl}/lists`);
    expect(createReq.request.method).toBe('POST');
    createReq.flush({});

    const updateReq = httpMock.expectOne(`${environment.apiUrl}/lists/4`);
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush({});

    const reorderReq = httpMock.expectOne(`${environment.apiUrl}/lists/reorder`);
    expect(reorderReq.request.method).toBe('PUT');
    reorderReq.flush([]);

    const archiveReq = httpMock.expectOne(`${environment.apiUrl}/lists/4/archive`);
    expect(archiveReq.request.method).toBe('PUT');
    archiveReq.flush({});

    const unarchiveReq = httpMock.expectOne(`${environment.apiUrl}/lists/4/unarchive`);
    expect(unarchiveReq.request.method).toBe('PUT');
    unarchiveReq.flush({});
  });

  it('covers move and delete endpoints', () => {
    service.move(4, 10, 3).subscribe();
    service.delete(4).subscribe();

    const moveReq = httpMock.expectOne(`${environment.apiUrl}/lists/4/move`);
    expect(moveReq.request.method).toBe('PUT');
    expect(moveReq.request.body).toEqual({ targetBoardId: 10, targetPosition: 3 });
    moveReq.flush({});

    const deleteReq = httpMock.expectOne(`${environment.apiUrl}/lists/4`);
    expect(deleteReq.request.method).toBe('DELETE');
    expect(deleteReq.request.responseType).toBe('text');
    deleteReq.flush('deleted');
  });
});
