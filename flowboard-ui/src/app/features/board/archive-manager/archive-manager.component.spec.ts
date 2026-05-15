import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArchiveManagerComponent } from './archive-manager.component';

describe('ArchiveManagerComponent', () => {
  let component: ArchiveManagerComponent;
  let fixture: ComponentFixture<ArchiveManagerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArchiveManagerComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ArchiveManagerComponent);
    component = fixture.componentInstance;
  });

  it('starts on cards tab and emits actions', () => {
    const cardSpy = jasmine.createSpy('card');
    const listSpy = jasmine.createSpy('list');
    const deleteCardSpy = jasmine.createSpy('deleteCard');
    const deleteListSpy = jasmine.createSpy('deleteList');
    const closedSpy = jasmine.createSpy('closed');

    component.restoreCard.subscribe(cardSpy);
    component.restoreList.subscribe(listSpy);
    component.deleteCard.subscribe(deleteCardSpy);
    component.deleteList.subscribe(deleteListSpy);
    component.closed.subscribe(closedSpy);

    expect(component.activeTab).toBe('cards');

    const card = { id: 1 } as any;
    component.onRestoreCard(card);
    component.onRestoreList(2);
    component.onDeleteCard(card);
    component.onDeleteList(3);
    component.close();

    expect(cardSpy).toHaveBeenCalledWith(card);
    expect(listSpy).toHaveBeenCalledWith(2);
    expect(deleteCardSpy).toHaveBeenCalledWith(card);
    expect(deleteListSpy).toHaveBeenCalledWith(3);
    expect(closedSpy).toHaveBeenCalled();
  });
});
