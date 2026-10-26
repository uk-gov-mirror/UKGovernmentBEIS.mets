import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IncludeActivityDataInfoComponent } from './include-activity-data-info.component';

describe('IncludeActivityDataInfoComponent', () => {
  let component: IncludeActivityDataInfoComponent;
  let fixture: ComponentFixture<IncludeActivityDataInfoComponent>;

  const createComponent = () => {
    fixture = TestBed.createComponent(IncludeActivityDataInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [],
    }).compileComponents();
  });

  beforeEach(() => createComponent());

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
