import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';

import { of, throwError } from 'rxjs';

import { PendingRequestService } from '@core/guards/pending-request.service';
import { ActivatedRouteStub, BasePage } from '@testing';

import { EmissionFactorsDetailDTO } from 'pmrv-api';

import { EmissionFactorsService, UploadedEmissionFactorsDetail } from '../emission-factors.service';
import { EmissionFactorsCheckYourAnswersComponent } from './emission-factors-check-your-answers.component';

describe('EmissionFactorsCheckYourAnswersComponent', () => {
  let fixture: ComponentFixture<EmissionFactorsCheckYourAnswersComponent>;
  let page: Page;
  let navigateSpy: jest.SpyInstance;

  const fileName = 'uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx';
  const file = new File(['content'], fileName);

  const detail: EmissionFactorsDetailDTO = {
    regionalNaturalGas: [
      { chargingZoneName: 'Eastern', chargingZoneCode: 'EA', emissionFactor: '56.7', netCalorificValue: '0.0374' },
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

  const uploadedDetail = signal<UploadedEmissionFactorsDetail | null>(null);
  const mockEmissionFactorsService = {
    uploadedDetail,
    submitEmissionFactors: jest.fn(),
    getEmissionFactors: jest.fn(),
  };

  class Page extends BasePage<EmissionFactorsCheckYourAnswersComponent> {
    get heading() {
      return this.query<HTMLHeadingElement>('h1');
    }
    get summaryKey() {
      return this.query<HTMLElement>('.govuk-summary-list__key');
    }
    get fileLink() {
      return this.query<HTMLAnchorElement>('.govuk-summary-list__value a');
    }
    get changeLinks() {
      return this.queryAll<HTMLAnchorElement>('a').filter((anchor) => anchor.textContent.trim().startsWith('Change'));
    }
    get tables() {
      return this.queryAll<HTMLTableElement>('table.govuk-table');
    }
    get sectionHeadings() {
      return this.queryAll<HTMLHeadingElement>('h2.govuk-heading-m').map((heading) => heading.textContent.trim());
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
    get confirmButton() {
      return this.queryAll<HTMLButtonElement>('button').find(
        (button) => button.textContent.trim() === 'Confirm and complete',
      );
    }
    get returnLink() {
      return this.queryAll('a').find((anchor) => anchor.textContent.trim() === 'Return to: Emission factors');
    }
    get errorSummaryItems() {
      return this.queryAll<HTMLLIElement>('.govuk-error-summary__list li').map((item) => item.textContent.trim());
    }
  }

  async function createComponent(
    uploaded: UploadedEmissionFactorsDetail = { referenceYear: 2026, file, fileUuid: 'pending-upload-uuid', detail },
  ) {
    uploadedDetail.set(uploaded);

    await TestBed.configureTestingModule({
      imports: [EmissionFactorsCheckYourAnswersComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: new ActivatedRouteStub() },
        { provide: EmissionFactorsService, useValue: mockEmissionFactorsService },
        { provide: PendingRequestService, useValue: { trackRequest: () => (source: unknown) => source } },
      ],
    }).compileComponents();

    navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(EmissionFactorsCheckYourAnswersComponent);
    page = new Page(fixture);
    fixture.detectChanges();
  }

  function confirm() {
    page.confirmButton.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    URL.createObjectURL = jest.fn(() => 'blob:uploaded-file');
    URL.revokeObjectURL = jest.fn();
    mockEmissionFactorsService.submitEmissionFactors.mockReturnValue(of(null));
    mockEmissionFactorsService.getEmissionFactors.mockReturnValue(of([]));
  });

  afterEach(() => jest.clearAllMocks());

  it('should create', async () => {
    await createComponent();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the Check your answers heading', async () => {
    await createComponent();

    expect(page.heading.textContent).toContain('Check your answers');
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('renders the uploaded file as a download link of the local file', async () => {
    await createComponent();

    expect(page.summaryKey.textContent.trim()).toEqual('Uploaded file');
    expect(page.fileLink.textContent.trim()).toEqual(fileName);
    expect(page.fileLink.getAttribute('href')).toEqual('blob:uploaded-file');
    expect(page.fileLink.getAttribute('download')).toEqual(fileName);
    expect(URL.createObjectURL).toHaveBeenCalledWith(file);
  });

  it('revokes the file download link when destroyed', async () => {
    await createComponent();

    fixture.destroy();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:uploaded-file');
  });

  it('offers a single Change link, which goes back to upload another file', async () => {
    await createComponent();

    expect(page.changeLinks).toHaveLength(1);
    expect(page.changeLinks[0].textContent).toContain('uploaded file');
    expect(page.changeLinks[0].getAttribute('href')).toBeTruthy();
  });

  it('renders the regional natural gas and national inventory tables', async () => {
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
    expect(page.rowCells(page.tables[0])).toEqual([
      ['Eastern', 'EA', '56.7', '0.0374'],
      ['Northern Ireland', 'NI', '56.5', '0.0375'],
    ]);
    expect(page.rowCells(page.tables[1])).toEqual([
      ['1A1ai Public electricity generation', 'Coal', '23.6', 'GJ/tonne', '1.0'],
      ['1A1b Petroleum Refining', 'All Petroleum Refining', '0.0425', 'GJ/m³', '1.0'],
    ]);
  });

  it('takes the year of the success notification from the uploaded data', async () => {
    await createComponent({ referenceYear: 2027, file, fileUuid: 'pending-upload-uuid', detail });

    confirm();

    expect(navigateSpy).toHaveBeenCalledWith(['../..'], {
      relativeTo: TestBed.inject(ActivatedRoute),
      state: { notification: 'Emission factors for 2027 have been added.' },
    });
  });

  it('says the emission factors were updated when the year already had emission factors at confirmation', async () => {
    mockEmissionFactorsService.getEmissionFactors.mockReturnValue(
      of([{ referenceYear: 2026, fileUuid: 'uuid-2026', fileName: fileName, uploadedAt: '2026-02-03T09:00:00Z' }]),
    );
    await createComponent();

    confirm();

    expect(navigateSpy).toHaveBeenCalledWith(['../..'], {
      relativeTo: TestBed.inject(ActivatedRoute),
      state: { notification: 'Emission factors for 2026 have been updated.' },
    });
  });

  it('still submits, saying the emission factors were added, when the existing emission factors cannot be loaded', async () => {
    mockEmissionFactorsService.getEmissionFactors.mockReturnValue(throwError(() => new Error('unavailable')));
    await createComponent();

    confirm();

    expect(mockEmissionFactorsService.submitEmissionFactors).toHaveBeenCalledWith();
    expect(navigateSpy).toHaveBeenCalledWith(['../..'], {
      relativeTo: TestBed.inject(ActivatedRoute),
      state: { notification: 'Emission factors for 2026 have been added.' },
    });
  });

  it('submits the reviewed upload and returns to emission factors with a success notification', async () => {
    await createComponent();

    confirm();

    expect(mockEmissionFactorsService.submitEmissionFactors).toHaveBeenCalledWith();
    expect(navigateSpy).toHaveBeenCalledWith(['../..'], {
      relativeTo: TestBed.inject(ActivatedRoute),
      state: { notification: 'Emission factors for 2026 have been added.' },
    });
  });

  it('shows the backend message when the submission is rejected', async () => {
    mockEmissionFactorsService.submitEmissionFactors.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 400, error: { code: 'FORM1001', message: 'Invalid fuel mapping' } }),
      ),
    );
    await createComponent();

    confirm();

    expect(page.errorSummaryItems).toEqual(['Invalid fuel mapping']);
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(page.confirmButton.disabled).toBe(false);
  });

  it('shows a generic message when the submission fails unexpectedly', async () => {
    mockEmissionFactorsService.submitEmissionFactors.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    await createComponent();

    confirm();

    expect(page.errorSummaryItems).toEqual([
      'Sorry, there was a problem submitting the emission factors. Try again later.',
    ]);
  });

  it('shows the values rounded half up to 4 decimals', async () => {
    await createComponent({
      referenceYear: 2026,
      file,
      fileUuid: 'pending-upload-uuid',
      detail: {
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
      },
    });

    expect(page.rowCells(page.tables[0])).toEqual([['Eastern', 'EA', '56.521', '0.0374']]);
    expect(page.rowCells(page.tables[1])).toEqual([['1A1a', 'Coal', '23.6261', 'GJ/tonne', '0.9828']]);
  });

  it('renders a working "Return to: Emission factors" link', async () => {
    await createComponent();

    expect(page.returnLink.getAttribute('href')).toBeTruthy();
  });
});
