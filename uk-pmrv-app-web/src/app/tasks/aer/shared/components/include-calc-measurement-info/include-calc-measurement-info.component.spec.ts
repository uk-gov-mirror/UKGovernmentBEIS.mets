import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IncludeCalcMeasurementInfoComponent } from './include-calc-measurement-info.component';

describe('IncludeCalcMeasurementInfoComponent', () => {
  let component: IncludeCalcMeasurementInfoComponent;
  let fixture: ComponentFixture<IncludeCalcMeasurementInfoComponent>;

  const createComponent = () => {
    fixture = TestBed.createComponent(IncludeCalcMeasurementInfoComponent);
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
