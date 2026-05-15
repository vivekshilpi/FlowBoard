import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BoardAnalyticsComponent } from './board-analytics.component';

describe('BoardAnalyticsComponent', () => {
  let component: BoardAnalyticsComponent;
  let fixture: ComponentFixture<BoardAnalyticsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BoardAnalyticsComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(BoardAnalyticsComponent);
    component = fixture.componentInstance;
  });

  it('calculates card statistics on init', () => {
    component.board = { id: 1 } as any;
    component.allCards = [
      { status: 'TO_DO', isOverdue: false, dueDate: null },
      { status: 'IN_PROGRESS', isOverdue: true, dueDate: '2026-05-15' },
      { status: 'DONE', isOverdue: false, dueDate: '2026-05-16' }
    ] as any;

    component.ngOnInit();

    expect(component.stats.total).toBe(3);
    expect(component.stats.todo).toBe(1);
    expect(component.stats.inProgress).toBe(1);
    expect(component.stats.done).toBe(1);
    expect(component.stats.overdue).toBe(1);
    expect(component.stats.withDueDates).toBe(2);
    expect(component.getPercentage(1)).toBe(33);
  });

  it('returns zero percentage when there are no cards', () => {
    component.stats.total = 0;
    expect(component.getPercentage(5)).toBe(0);
  });
});
