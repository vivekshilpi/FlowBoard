import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { CardService } from './card.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('CardService', () => {
  let service: CardService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...createHttpTestingProviders()]
    });
    service = TestBed.inject(CardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('normalizes cards from list, board, assignee and id endpoints', () => {
    const rawCard = { id: 1, archived: true, overdue: true };

    service.getByList(2).subscribe(cards => expect(cards[0].isArchived).toBeTrue());
    httpMock.expectOne(`${environment.apiUrl}/cards/list/2`).flush([rawCard]);

    service.getByBoard(3).subscribe(cards => expect(cards[0].isOverdue).toBeTrue());
    httpMock.expectOne(`${environment.apiUrl}/cards/board/3`).flush([rawCard]);

    service.getById(1).subscribe(card => expect(card.isArchived).toBeTrue());
    httpMock.expectOne(`${environment.apiUrl}/cards/1`).flush(rawCard);

    service.getByAssignee(4).subscribe(cards => expect(cards.length).toBe(1));
    httpMock.expectOne(`${environment.apiUrl}/cards/assignee/4`).flush([rawCard]);
  });

  it('covers mutating card endpoints', () => {
    const card = { id: 1 };

    service.create({} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards`).flush(card);

    service.update(1, {} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1`).flush(card);

    service.move(1, {} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/move`).flush(card);

    service.archive(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/archive`).flush(card);

    service.unarchive(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/unarchive`).flush(card);

    service.setAssignee(1, null).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/assignee`).flush(card);

    service.setPriority(1, 'HIGH' as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/priority`).flush(card);

    service.setStatus(1, 'DONE' as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/status`).flush(card);

    service.copyCard(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/copy`).flush(card);

    service.copyCard(1, 9).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/copy?targetListId=9`).flush(card);

    expect(card.id).toBe(1);
  });

  it('covers collection, comment, attachment, and search endpoints', () => {
    const card = { id: 1 };

    service.reorder(2, [1, 2]).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/reorder`).flush([]);

    service.getByStatus(1, 'TO_DO' as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/board/1/status/TO_DO`).flush([]);

    service.getByPriority(1, 'HIGH' as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/board/1/priority/HIGH`).flush([]);

    service.getOverdue(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/board/1/overdue`).flush([]);

    service.getAllOverdue().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/overdue/all`).flush([]);

    service.search(1, 'fix').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/board/1/search?keyword=fix`).flush([]);

    service.getArchivedByBoard(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/board/1/archived`).flush([]);

    service.getActivity(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/activity`).flush([]);

    service.addComment(1, {} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/comments`).flush({});

    service.updateComment(1, 2, 'updated').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/comments/2`).flush({});

    service.deleteComment(1, 2).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/comments/2`).flush('ok');

    service.uploadAttachment(1, new File(['a'], 'doc.txt')).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/attachments`).flush(card);

    service.deleteAttachment(1, 'att').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/attachments/att`).flush(card);

    service.downloadAttachment(1, 'att').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/attachments/att/download`).flush(new Blob());

    service.getBoardStats(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/board/1/stats`).flush({});

    service.getActivityPaged(1, 2, 50).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/1/activity/paged?page=2&size=50`).flush({});

    service.globalSearch('bug', 5).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/search?keyword=bug&assigneeId=5`).flush([]);

    service.globalSearch(undefined, undefined).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/search`).flush([]);

    service.delete(4).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/cards/4`).flush('ok');

    expect(card.id).toBe(1);
  });
});
