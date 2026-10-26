import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Navigation, provideRouter, Router } from '@angular/router';

import { of } from 'rxjs';

import { BasePage } from '@testing';

import { EmissionFactorsHistoryEntryDTO } from 'pmrv-api';

import { SettingsEmissionFactorsPermissionService } from '../core/settings-emission-factors-permission.service';
import { EmissionFactorsComponent } from './emission-factors.component';
import { EmissionFactorsService } from './emission-factors.service';

describe('EmissionFactorsComponent', () => {
  let fixture: ComponentFixture<EmissionFactorsComponent>;
  let page: Page;

  const rows: EmissionFactorsHistoryEntryDTO[] = [
    { referenceYear: 2025, fileUuid: 'uuid-2025', fileName: 'ghg-factors-2025.xlsx', uploadedAt: '2025-01-14' },
    { referenceYear: 2026, fileUuid: 'uuid-2026', fileName: 'ghg-factors-2026.xlsx', uploadedAt: '2026-02-03' },
  ];

  const mockEmissionFactorsService = { getEmissionFactors: () => of(rows) };
  const mockPermissionService = { canExecute: () => of(false) };

  class Page extends BasePage<EmissionFactorsComponent> {
    get heading() {
      return this.query<HTMLHeadingElement>('h1');
    }
    get headers() {
      return this.queryAll<HTMLTableCellElement>('.govuk-table__head .govuk-table__header');
    }
    get rows() {
      return this.queryAll<HTMLTableRowElement>('.govuk-table__body .govuk-table__row');
    }
    get emptyTableText() {
      return this.query<HTMLElement>('.govuk-table__cell--empty--no-bottom-border');
    }
    get yearSortButton() {
      return this.query<HTMLButtonElement>('.govuk-table__head button');
    }
    get notificationBanner() {
      return this.query<HTMLElement>('govuk-notification-banner');
    }
    get viewHistoryLink() {
      return this.queryAll<HTMLAnchorElement>('a').find((a) => a.textContent.trim() === 'View history');
    }
    get addButton() {
      return this.queryAll<HTMLAnchorElement>('a').find((a) => a.textContent.trim() === 'Add emission factors');
    }

    yearLink(row: HTMLTableRowElement) {
      return row.querySelectorAll('a')[0];
    }

    fileCell(row: HTMLTableRowElement) {
      return row.querySelectorAll('td')[0];
    }
  }

  async function createComponent(
    service = mockEmissionFactorsService,
    permissionService = mockPermissionService,
    notification?: string,
  ) {
    await TestBed.configureTestingModule({
      imports: [EmissionFactorsComponent],
      providers: [
        provideRouter([]),
        { provide: EmissionFactorsService, useValue: service },
        { provide: SettingsEmissionFactorsPermissionService, useValue: permissionService },
      ],
    }).compileComponents();

    if (notification) {
      jest
        .spyOn(TestBed.inject(Router), 'currentNavigation')
        .mockReturnValue({ extras: { state: { notification } } } as unknown as Navigation);
    }

    fixture = TestBed.createComponent(EmissionFactorsComponent);
    page = new Page(fixture);
    fixture.detectChanges();
  }

  it('should create', async () => {
    await createComponent();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('links "View history" below the table to the emission factors history page', async () => {
    await createComponent();

    expect(page.viewHistoryLink.getAttribute('href')).toBe('/history');
    expect(
      page.viewHistoryLink.compareDocumentPosition(fixture.nativeElement.querySelector('govuk-table')) &
        Node.DOCUMENT_POSITION_PRECEDING,
    ).toBeTruthy();
  });

  it('renders the Emission factors heading', async () => {
    await createComponent();

    expect(page.heading.textContent.trim()).toEqual('Emission factors');
  });

  it('does not show the success banner without a notification', async () => {
    await createComponent();

    expect(page.notificationBanner).toBeFalsy();
  });

  it('shows the success banner when navigated to with a notification', async () => {
    await createComponent(
      mockEmissionFactorsService,
      mockPermissionService,
      'Emission factors for 2026 have been added.',
    );

    expect(page.notificationBanner.textContent).toContain('Emission factors for 2026 have been added.');
  });

  it('renders the Reference year, File and Last updated column headers', async () => {
    await createComponent();

    expect(page.headers.map((header) => header.textContent.trim())).toEqual(['Reference year', 'File', 'Last updated']);
  });

  it('sorts rows descending by reference year by default', async () => {
    await createComponent();

    expect(page.rows.map((row) => row.querySelector('th').textContent.trim())).toEqual(['2026', '2025']);
  });

  it('re-sorts ascending when the Reference year header is clicked', async () => {
    await createComponent();

    page.yearSortButton.click();
    fixture.detectChanges();

    expect(page.rows.map((row) => row.querySelector('th').textContent.trim())).toEqual(['2025', '2026']);
  });

  it('links the Reference year cell to that year', async () => {
    await createComponent();

    expect(page.yearLink(page.rows[0]).getAttribute('href')).toEqual('/2026');
  });

  it('links the File cell to the download page for that year in a new tab', async () => {
    await createComponent();

    const link = page.fileCell(page.rows[1]).querySelector('a');

    expect(link.textContent.trim()).toEqual('ghg-factors-2025.xlsx');
    expect(link.getAttribute('href')).toEqual('/file-download/2025');
    expect(link.getAttribute('target')).toEqual('_blank');
  });

  it('formats the Last updated cell as d MMM yyyy', async () => {
    await createComponent();

    expect(page.rows[1].querySelectorAll('td')[1].textContent.trim()).toEqual('14 Jan 2025');
  });

  it('shows the empty state when there are no emission factor rows', async () => {
    await createComponent({ getEmissionFactors: () => of([]) });

    expect(page.emptyTableText.textContent).toContain('No emission factor files have been uploaded yet.');
  });

  it('hides the Add emission factors button when the user cannot execute emission factors actions', async () => {
    await createComponent(mockEmissionFactorsService, { canExecute: () => of(false) });

    expect(page.addButton).toBeFalsy();
  });

  it('shows a working Add emission factors button linking to upload when the user can execute emission factors actions', async () => {
    await createComponent(mockEmissionFactorsService, { canExecute: () => of(true) });

    expect(page.addButton).toBeTruthy();
    expect(page.addButton.getAttribute('href')).toEqual('/upload');
  });
});
