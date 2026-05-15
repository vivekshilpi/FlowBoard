import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListColumnComponent } from './list-column.component';

describe('ListColumnComponent', () => {
  let component: ListColumnComponent;
  let fixture: ComponentFixture<ListColumnComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListColumnComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ListColumnComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
