import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CardDetailComponent } from './card-detail.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';

describe('CardDetailComponent', () => {
  let component: CardDetailComponent;
  let fixture: ComponentFixture<CardDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardDetailComponent],
      providers: [...createComponentTestingProviders({ includeStore: true })]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CardDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
