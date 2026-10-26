import { TestBed } from '@angular/core/testing';

import { firstValueFrom, of, throwError } from 'rxjs';

import {
  EmissionFactorsDetailDTO,
  EmissionFactorsHistoryEntryDTO,
  EmissionFactorsUploadDTO,
  SettingsService,
} from 'pmrv-api';

import { EmissionFactorsService } from './emission-factors.service';

function xlsxFile(): File {
  return new File(['content'], 'uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx');
}

describe('EmissionFactorsService', () => {
  let service: EmissionFactorsService;
  const settingsService = {
    getEmissionFactors: jest.fn(),
    getEmissionFactorsDetail: jest.fn(),
    uploadEmissionFactors: jest.fn(),
    submitEmissionFactors: jest.fn(),
    getEmissionFactorsHistory: jest.fn(),
  };

  const entries: EmissionFactorsHistoryEntryDTO[] = [
    { referenceYear: 2024, fileUuid: 'uuid-2024', fileName: 'factors-2024.xlsx', uploadedAt: '2024-01-10T09:00:00Z' },
    { referenceYear: 2026, fileUuid: 'uuid-2026', fileName: 'factors-2026.xlsx', uploadedAt: '2026-02-03T09:00:00Z' },
  ];

  const detail: EmissionFactorsDetailDTO = {
    regionalNaturalGas: [
      {
        chargingZoneCode: 'EA',
        chargingZoneName: 'Eastern',
        emissionFactor: '56.7',
        netCalorificValue: '0.0374',
        calculationFactor: '0.9476',
      },
    ],
    nationalInventory: [
      {
        sectorName: '1A1ai Public electricity generation',
        ukGhgFuel: 'Coal',
        emissionFactor: '94.6',
        netCalorificValue: '23.6',
        oxidationFactor: '1.0',
        ncvMeasurementUnit: 'GJ_PER_TONNE',
        efMeasurementUnit: 'TONNES_OF_CO2_PER_TJ',
      },
    ],
  };

  const upload: EmissionFactorsUploadDTO = { fileUuid: 'pending-upload-uuid', data: detail };

  beforeEach(() => {
    settingsService.getEmissionFactors.mockReturnValue(of(entries));
    settingsService.getEmissionFactorsDetail.mockReturnValue(of(detail));
    settingsService.uploadEmissionFactors.mockReturnValue(of(upload));
    settingsService.submitEmissionFactors.mockReturnValue(of(undefined));

    TestBed.configureTestingModule({ providers: [{ provide: SettingsService, useValue: settingsService }] });

    service = TestBed.inject(EmissionFactorsService);
  });

  afterEach(() => jest.clearAllMocks());

  it('returns the emission factors entries from SettingsService', async () => {
    expect(await firstValueFrom(service.getEmissionFactors())).toEqual(entries);
  });

  it('requests the history with a 0-based page and maps the records for display', async () => {
    settingsService.getEmissionFactorsHistory.mockReturnValue(
      of({
        history: [{ changedAt: '2026-06-21T11:20:00.000Z', changedBy: 'Jane Smith', referenceYear: 2026 }],
        totalItems: 31,
      }),
    );

    const result = await firstValueFrom(service.getEmissionFactorsHistory(2, 30));

    expect(settingsService.getEmissionFactorsHistory).toHaveBeenCalledWith(1, 30);
    expect(result).toEqual({
      records: [{ changedAtDisplay: '21 Jun 2026, 12:20pm', changedBy: 'Jane Smith', referenceYear: 2026 }],
      total: 31,
    });
  });

  it('returns an empty history page when the response has no records', async () => {
    settingsService.getEmissionFactorsHistory.mockReturnValue(of({}));

    expect(await firstValueFrom(service.getEmissionFactorsHistory(1))).toEqual({ records: [], total: 0 });
    expect(settingsService.getEmissionFactorsHistory).toHaveBeenCalledWith(0, 30);
  });

  it('returns the emission factors detail for a reference year from SettingsService', async () => {
    expect(await firstValueFrom(service.getEmissionFactorsDetail(2026))).toEqual(detail);
    expect(settingsService.getEmissionFactorsDetail).toHaveBeenCalledWith(2026);
  });

  it('delegates uploadEmissionFactors to SettingsService and returns the parsed detail', async () => {
    const file = xlsxFile();

    expect(await firstValueFrom(service.uploadEmissionFactors(2026, file))).toEqual(detail);
    expect(settingsService.uploadEmissionFactors).toHaveBeenCalledWith(2026, file);
  });

  it('keeps the uploaded detail, file and pending upload for check your answers', async () => {
    const file = xlsxFile();

    expect(service.uploadedDetail()).toBeNull();

    await firstValueFrom(service.uploadEmissionFactors(2026, file));

    expect(service.uploadedDetail()).toEqual({
      referenceYear: 2026,
      detail,
      file,
      fileUuid: 'pending-upload-uuid',
    });
  });

  it('clears the previous upload when a new upload fails', async () => {
    await firstValueFrom(service.uploadEmissionFactors(2026, xlsxFile()));
    settingsService.uploadEmissionFactors.mockReturnValue(throwError(() => new Error('wrong year')));

    await expect(firstValueFrom(service.uploadEmissionFactors(2026, xlsxFile()))).rejects.toThrow('wrong year');

    expect(service.uploadedDetail()).toBeNull();
  });

  it('submits the pending upload that was reviewed and clears the uploaded detail', async () => {
    await firstValueFrom(service.uploadEmissionFactors(2026, xlsxFile()));

    await firstValueFrom(service.submitEmissionFactors(), { defaultValue: undefined });

    expect(settingsService.submitEmissionFactors).toHaveBeenCalledWith('pending-upload-uuid');
    expect(service.uploadedDetail()).toBeNull();
  });

  it('keeps the uploaded detail when the submission fails so it can be retried', async () => {
    const file = xlsxFile();
    await firstValueFrom(service.uploadEmissionFactors(2026, file));
    settingsService.submitEmissionFactors.mockReturnValue(throwError(() => new Error('failed')));

    await expect(firstValueFrom(service.submitEmissionFactors())).rejects.toThrow('failed');

    expect(service.uploadedDetail()).not.toBeNull();
  });

  it('fails the submission when nothing has been uploaded', async () => {
    await expect(firstValueFrom(service.submitEmissionFactors())).rejects.toThrow(
      'There is no uploaded emission factors file to submit',
    );
    expect(settingsService.submitEmissionFactors).not.toHaveBeenCalled();
  });
});
