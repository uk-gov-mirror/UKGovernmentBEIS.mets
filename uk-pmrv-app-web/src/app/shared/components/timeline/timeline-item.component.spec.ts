import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';

import { RequestActionInfoDTO } from 'pmrv-api';

import { SharedModule } from '../../shared.module';
import { TimelineItemComponent } from './timeline-item.component';

describe('TimelineItemComponent', () => {
  let component: TimelineItemComponent;
  let fixture: ComponentFixture<TimelineItemComponent>;

  const createComponent = (action: RequestActionInfoDTO): ComponentFixture<TimelineItemComponent> => {
    const newFixture = TestBed.createComponent(TimelineItemComponent);
    newFixture.componentInstance.action = action;
    newFixture.componentInstance.link = ['.'];
    newFixture.detectChanges();
    return newFixture;
  };

  const linkAccessibleName = (target: ComponentFixture<TimelineItemComponent>): string =>
    (target.nativeElement as HTMLElement).querySelector('a').textContent.replace(/\s+/g, ' ').trim();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = createComponent({
      type: 'PERMIT_ISSUANCE_APPLICATION_SUBMITTED',
      creationDate: '2020-08-25 10:36:15.189643',
      submitter: 'asd',
    });
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should add the action header and the creation date as visually hidden link text', () => {
    const element: HTMLElement = fixture.nativeElement;
    const hiddenText = element.querySelector('a .govuk-visually-hidden').textContent.trim();

    expect(hiddenText).toEqual(
      `of ${element.querySelector('h3').textContent.trim()}, ${element.querySelector('p').textContent.trim()}`,
    );
    expect(linkAccessibleName(fixture)).toMatch(/^View details of /);
    expect(hiddenText).toContain('25 Aug 2020');
    expect(hiddenText).toContain('by asd');
  });

  it('should give distinguishable link names to actions of the same type and submitter', () => {
    const action: RequestActionInfoDTO = {
      type: 'WASTE_QDR_REGULATOR_REVIEW_RETURNED_FOR_AMENDS',
      submitter: 'Regulator User',
    };
    const first = createComponent({ ...action, creationDate: '2026-02-12T11:02:00Z' });
    const second = createComponent({ ...action, creationDate: '2026-02-11T14:22:00Z' });

    expect(first.nativeElement.querySelector('h3').textContent).toEqual(
      second.nativeElement.querySelector('h3').textContent,
    );
    expect(linkAccessibleName(first)).not.toEqual(linkAccessibleName(second));
  });
});
