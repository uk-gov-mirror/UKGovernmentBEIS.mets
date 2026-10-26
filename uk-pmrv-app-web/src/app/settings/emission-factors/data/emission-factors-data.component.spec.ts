import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { of } from 'rxjs';

import { ActivatedRouteStub, BasePage } from '@testing';

import { EmissionFactorsDetailDTO, EmissionFactorsHistoryEntryDTO } from 'pmrv-api';

import { SettingsEmissionFactorsPermissionService } from '../../core/settings-emission-factors-permission.service';
import { EmissionFactorsService } from '../emission-factors.service';
import { EmissionFactorsDataComponent } from './emission-factors-data.component';

describe('EmissionFactorsDataComponent', () => {
  let fixture: ComponentFixture<EmissionFactorsDataComponent>;
  let page: Page;

  const detail: EmissionFactorsDetailDTO = {
    regionalNaturalGas: [
      { chargingZoneName: 'Eastern', chargingZoneCode: 'EA', emissionFactor: '56.7', netCalorificValue: '0.0374' },
      { chargingZoneName: 'Stranraer', chargingZoneCode: 'ST', emissionFactor: '56.5', netCalorificValue: '0.0375' },
      {
        chargingZoneName: 'Northern Ireland',
        chargingZoneCode: 'NI',
        emissionFactor: '56.5',
        netCalorificValue: '0.0375',
      },
    ],
    nationalInventory: [
      {
        sectorName: '1A1ai Public electricity generation',
        ukGhgFuel: 'Coal',
        netCalorificValue: '23.6',
        ncvMeasurementUnit: 'GJ_PER_TONNE',
        oxidationFactor: '1.0',
      },
      {
        sectorName: '1A1b Petroleum Refining',
        ukGhgFuel: 'All Petroleum Refining',
        netCalorificValue: '0.0425',
        ncvMeasurementUnit: 'GJ_PER_NM3',
        oxidationFactor: '1.0',
      },
    ],
  };

  const entries: EmissionFactorsHistoryEntryDTO[] = [
    { referenceYear: 2025, fileUuid: 'uuid-2025', fileName: 'factors-2025.xlsx', uploadedAt: '2025-01-10T09:00:00Z' },
    { referenceYear: 2026, fileUuid: 'uuid-2026', fileName: 'factors-2026.xlsx', uploadedAt: '2026-02-03T09:00:00Z' },
  ];

  const mockEmissionFactorsService = { getEmissionFactorsDetail: jest.fn(), getEmissionFactors: jest.fn() };
  const mockPermissionService = { canExecute: jest.fn() };
  const currentYear = new Date().getFullYear();

  class Page extends BasePage<EmissionFactorsDataComponent> {
    get heading() {
      return this.query<HTMLHeadingElement>('h1');
    }
    get sectionHeadings() {
      return this.queryAll<HTMLHeadingElement>('h2.govuk-heading-m').map((heading) => heading.textContent.trim());
    }
    get tables() {
      return this.queryAll<HTMLTableElement>('table.govuk-table');
    }
    get headers() {
      return this.tables.map((table) =>
        Array.from(table.querySelectorAll('.govuk-table__head .govuk-table__header')).map((th) =>
          th.textContent.trim(),
        ),
      );
    }
    rowCells(table: HTMLTableElement) {
      return Array.from(table.querySelectorAll('.govuk-table__body .govuk-table__row')).map((row) =>
        Array.from(row.querySelectorAll(':scope > th, :scope > td')).map((cell) => cell.textContent.trim()),
      );
    }
    get buttons() {
      return this.queryAll<HTMLButtonElement>('button');
    }
    get warningText() {
      return this.query<HTMLElement>('.govuk-warning-text__text');
    }
    get summaryListRows() {
      return this.queryAll<HTMLDivElement>('.govuk-summary-list__row').map((row) => [
        row.querySelector('dt').textContent.trim(),
        row.querySelector('dd').textContent.trim(),
      ]);
    }
    get uploadedFileLink() {
      return this.query<HTMLAnchorElement>('.govuk-summary-list__value a');
    }
    get paragraphs() {
      return this.queryAll<HTMLParagraphElement>('p.govuk-body').map((p) => p.textContent.trim());
    }
    get returnLink() {
      return this.queryAll('a').find((anchor) => anchor.textContent.trim() === 'Return to: Emission factors');
    }
    get updateDataButton() {
      return this.queryAll<HTMLAnchorElement>('a.govuk-button').find(
        (anchor) => anchor.textContent.trim() === 'Update data',
      );
    }
  }

  async function createComponent(referenceYear = '2026') {
    await TestBed.configureTestingModule({
      imports: [EmissionFactorsDataComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: new ActivatedRouteStub({ referenceYear }) },
        { provide: EmissionFactorsService, useValue: mockEmissionFactorsService },
        { provide: SettingsEmissionFactorsPermissionService, useValue: mockPermissionService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmissionFactorsDataComponent);
    page = new Page(fixture);
    fixture.detectChanges();
  }

  beforeEach(() => {
    mockEmissionFactorsService.getEmissionFactorsDetail.mockReturnValue(of(detail));
    mockEmissionFactorsService.getEmissionFactors.mockReturnValue(of(entries));
    mockPermissionService.canExecute.mockReturnValue(of(true));
  });

  afterEach(() => jest.clearAllMocks());

  it('should create', async () => {
    await createComponent();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads the submitted data for the year of the route', async () => {
    await createComponent();

    expect(mockEmissionFactorsService.getEmissionFactorsDetail).toHaveBeenCalledWith(2026);
    expect(page.heading.textContent.trim()).toEqual('Emission factors data for 2026');
  });

  it('renders nothing until the data has loaded', async () => {
    mockEmissionFactorsService.getEmissionFactorsDetail.mockReturnValue(of());
    await createComponent();

    expect(page.heading).toBeNull();
  });

  it('renders nothing until the uploaded files have loaded', async () => {
    mockEmissionFactorsService.getEmissionFactors.mockReturnValue(of());
    await createComponent();

    expect(page.heading).toBeNull();
  });

  it('warns that only the current reporting year can be updated', async () => {
    await createComponent();

    expect(page.warningText.textContent).toContain('Only data for the current reporting year can be updated.');
  });

  it('renders the file uploaded for the year as a download link', async () => {
    await createComponent();

    expect(page.summaryListRows).toEqual([['Uploaded file', 'factors-2026.xlsx']]);
    expect(page.uploadedFileLink.getAttribute('target')).toEqual('_blank');
  });

  it('links the uploaded file to the download page of the year', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'emission-factors/:referenceYear', component: EmissionFactorsDataComponent }]),
        { provide: EmissionFactorsService, useValue: mockEmissionFactorsService },
      ],
    });

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/emission-factors/2026');

    expect(harness.routeNativeElement.querySelector('.govuk-summary-list__value a').getAttribute('href')).toEqual(
      '/emission-factors/file-download/2026',
    );
  });

  it('explains that the net calorific values have been converted', async () => {
    await createComponent();

    expect(page.paragraphs).toContain(
      'Net calorific value data from the uploaded file has automatically been converted from MJ/m³ to GJ/m³',
    );
  });

  it('renders both tables with their column headers', async () => {
    await createComponent();

    expect(page.sectionHeadings).toEqual(['Regional natural gas emission factors', 'National inventory data criteria']);
    expect(page.headers).toEqual([
      [
        'Local Distribution Zone (LDZ)',
        'LDZ abbreviation',
        'Emission factor (tCO2/TJ net)',
        'Net calorific value (GJ/Nm³)',
      ],
      ['CRT category', 'UK GHG fuel', 'Net calorific value', 'Unit', 'Oxidation factor'],
    ]);
  });

  it('renders the regional natural gas rows, including the Northern Ireland row', async () => {
    await createComponent();

    expect(page.rowCells(page.tables[0])).toEqual([
      ['Eastern', 'EA', '56.7', '0.0374'],
      ['Stranraer', 'ST', '56.5', '0.0375'],
      ['Northern Ireland', 'NI', '56.5', '0.0375'],
    ]);
  });

  it('renders the national inventory rows with their unit labels', async () => {
    await createComponent();

    expect(page.rowCells(page.tables[1])).toEqual([
      ['1A1ai Public electricity generation', 'Coal', '23.6', 'GJ/tonne', '1.0'],
      ['1A1b Petroleum Refining', 'All Petroleum Refining', '0.0425', 'GJ/m³', '1.0'],
    ]);
  });

  it('does not offer to submit the data again', async () => {
    await createComponent();

    expect(page.buttons).toEqual([]);
  });

  it('renders a working "Return to: Emission factors" link', async () => {
    await createComponent();

    expect(page.returnLink.getAttribute('href')).toBeTruthy();
  });

  it('shows the values rounded half up to 4 decimals', async () => {
    mockEmissionFactorsService.getEmissionFactorsDetail.mockReturnValue(
      of({
        regionalNaturalGas: [
          {
            chargingZoneName: 'Eastern',
            chargingZoneCode: 'EA',
            emissionFactor: '56.5210000001',
            netCalorificValue: '0.037392992823976',
          },
        ],
        nationalInventory: [
          {
            sectorName: '1A1a',
            ukGhgFuel: 'Coal',
            netCalorificValue: '23.62609594079182',
            ncvMeasurementUnit: 'GJ_PER_TONNE',
            oxidationFactor: '0.9827957983557349',
          },
        ],
      }),
    );
    await createComponent();

    expect(page.rowCells(page.tables[0])).toEqual([['Eastern', 'EA', '56.521', '0.0374']]);
    expect(page.rowCells(page.tables[1])).toEqual([['1A1a', 'Coal', '23.6261', 'GJ/tonne', '0.9828']]);
  });

  describe('Update data', () => {
    it('is offered for the current reporting year to users who can upload emission factors', async () => {
      await createComponent(String(currentYear));

      expect(page.updateDataButton.classList).toContain('govuk-button--secondary');
    });

    it('is not offered for a previous reporting year', async () => {
      await createComponent(String(currentYear - 1));

      expect(page.updateDataButton).toBeUndefined();
    });

    it('is not offered to users who cannot upload emission factors', async () => {
      mockPermissionService.canExecute.mockReturnValue(of(false));
      await createComponent(String(currentYear));

      expect(page.updateDataButton).toBeUndefined();
    });
  });
});
