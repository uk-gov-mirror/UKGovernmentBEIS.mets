import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { EmissionFactorsDetailDTO } from 'pmrv-api';

import { EmissionFactorsService, UploadedEmissionFactorsDetail } from '../emission-factors.service';
import { canActivateEmissionFactorsSummary } from './emission-factors-summary.guard';

describe('canActivateEmissionFactorsSummary', () => {
  const uploadedDetail = signal<UploadedEmissionFactorsDetail | null>(null);

  async function navigateToSummary(): Promise<string> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'upload',
            children: [
              { path: '', children: [] },
              { path: 'summary', canActivate: [canActivateEmissionFactorsSummary], children: [] },
            ],
          },
        ]),
        { provide: EmissionFactorsService, useValue: { uploadedDetail } },
      ],
    });

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/upload/summary');

    return TestBed.inject(Router).url;
  }

  it('allows check your answers when a file has been uploaded', async () => {
    uploadedDetail.set({
      referenceYear: 2026,
      file: new File(['content'], 'uk-ggi-carbon-emission-factors-and-calorific-values-2026.xlsx'),
      fileUuid: 'pending-upload-uuid',
      detail: { regionalNaturalGas: [], nationalInventory: [] } as EmissionFactorsDetailDTO,
    });

    expect(await navigateToSummary()).toEqual('/upload/summary');
  });

  it('redirects to the upload page when nothing has been uploaded', async () => {
    uploadedDetail.set(null);

    expect(await navigateToSummary()).toEqual('/upload');
  });
});
