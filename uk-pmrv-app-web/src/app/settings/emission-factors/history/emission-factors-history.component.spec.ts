import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { RouterTestingHarness, RouterTestingModule } from '@angular/router/testing';

import { of } from 'rxjs';

import { ActivatedRouteStub, BasePage } from '@testing';

import {
  EmissionFactorsHistoryPage,
  EmissionFactorsHistoryRecord,
  EmissionFactorsService,
} from '../emission-factors.service';
import { EmissionFactorsHistoryComponent } from './emission-factors-history.component';

describe('EmissionFactorsHistoryComponent', () => {
  let fixture: ComponentFixture<EmissionFactorsHistoryComponent>;
  let page: Page;

  const record = (overrides: Partial<EmissionFactorsHistoryRecord> = {}): EmissionFactorsHistoryRecord => ({
    changedAtDisplay: '21 Jun 2026, 12:20pm',
    changedBy: 'Jane Smith',
    referenceYear: 2026,
    ...overrides,
  });

  const mockEmissionFactorsService = { getEmissionFactorsHistory: jest.fn() };

  class Page extends BasePage<EmissionFactorsHistoryComponent> {
    get heading() {
      return this.query<HTMLHeadingElement>('h1');
    }
    get recordHeadings() {
      return this.queryAll<HTMLHeadingElement>('h2').map((heading) => heading.textContent.trim());
    }
    get summaryLists() {
      return this.queryAll<HTMLElement>('dl').map((list) =>
        Array.from(list.querySelectorAll('.govuk-summary-list__row')).map((row) => [
          row.querySelector('dt').textContent.trim(),
          row.querySelector('dd').textContent.trim(),
        ]),
      );
    }
    get pagination() {
      return this.query('app-pagination');
    }
  }

  const createComponent = (historyPage: EmissionFactorsHistoryPage) => {
    mockEmissionFactorsService.getEmissionFactorsHistory.mockReturnValue(of(historyPage));

    fixture = TestBed.createComponent(EmissionFactorsHistoryComponent);
    page = new Page(fixture);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    mockEmissionFactorsService.getEmissionFactorsHistory.mockReset();

    await TestBed.configureTestingModule({
      imports: [EmissionFactorsHistoryComponent, RouterTestingModule],
      providers: [
        { provide: EmissionFactorsService, useValue: mockEmissionFactorsService },
        { provide: ActivatedRoute, useValue: new ActivatedRouteStub() },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    createComponent({ records: [], total: 0 });
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the Emission factors history heading', () => {
    createComponent({ records: [], total: 0 });
    expect(page.heading.textContent.trim()).toEqual('Emission factors history');
  });

  it('requests page 1 with a page size of 30 on load', () => {
    createComponent({ records: [], total: 0 });
    expect(mockEmissionFactorsService.getEmissionFactorsHistory).toHaveBeenCalledWith(1, 30);
  });

  it('shows a message when there is no history', () => {
    createComponent({ records: [], total: 0 });

    expect(fixture.nativeElement.textContent).toContain('No emission factors history available yet.');
    expect(page.recordHeadings).toEqual([]);
  });

  it('renders each record titled with its date and time, in the order received', () => {
    createComponent({
      records: [
        record(),
        record({ changedAtDisplay: '3 Feb 2026, 9:05am', changedBy: 'John Brown', referenceYear: 2025 }),
      ],
      total: 2,
    });

    expect(page.recordHeadings).toEqual(['21 Jun 2026, 12:20pm', '3 Feb 2026, 9:05am']);
    expect(page.summaryLists).toEqual([
      [
        ['Changed by', 'Jane Smith'],
        ['Reference year', '2026'],
        ['Emission factors data', 'View emission factors data'],
      ],
      [
        ['Changed by', 'John Brown'],
        ['Reference year', '2025'],
        ['Emission factors data', 'View emission factors data'],
      ],
    ]);
  });

  it('links each record to the emission factors data of its reference year', async () => {
    mockEmissionFactorsService.getEmissionFactorsHistory.mockReturnValue(
      of({ records: [record({ referenceYear: 2025 })], total: 1 }),
    );
    TestBed.resetTestingModule().configureTestingModule({
      providers: [
        provideRouter([{ path: 'settings/emission-factors/history', component: EmissionFactorsHistoryComponent }]),
        { provide: EmissionFactorsService, useValue: mockEmissionFactorsService },
      ],
    });

    const harness = await RouterTestingHarness.create('/settings/emission-factors/history');
    const link = Array.from(harness.routeNativeElement.querySelectorAll<HTMLAnchorElement>('a')).find(
      (anchor) => anchor.textContent.trim() === 'View emission factors data',
    );

    expect(link.getAttribute('href')).toEqual('/settings/emission-factors/2025');
  });

  it('does not show pagination for 30 records or fewer', () => {
    createComponent({ records: [record()], total: 30 });
    expect(page.pagination).toBeNull();
  });

  it('shows pagination for more than 30 records and loads the selected page', () => {
    createComponent({ records: [record()], total: 31 });
    expect(page.pagination).toBeTruthy();

    fixture.componentInstance.onPageChange(2);
    fixture.detectChanges();

    expect(mockEmissionFactorsService.getEmissionFactorsHistory).toHaveBeenLastCalledWith(2, 30);
  });
});
