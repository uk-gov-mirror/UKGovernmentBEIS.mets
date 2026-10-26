import { Routes } from '@angular/router';

import { PendingRequestGuard } from '@core/guards/pending-request.guard';

import { canExecuteSettingsEmissionFactors } from '../core/settings-emission-factors-permission.guard';
import { canActivateEmissionFactorsSummary } from './check-your-answers/emission-factors-summary.guard';

export const EMISSION_FACTORS_ROUTES: Routes = [
  {
    path: '',
    data: { pageTitle: 'Emission factors' },
    loadComponent: () => import('./emission-factors.component').then((m) => m.EmissionFactorsComponent),
  },
  {
    path: 'file-download/:referenceYear',
    data: { pageTitle: 'Download emission factors file' },
    loadComponent: () =>
      import('./file-download/emission-factors-file-download.component').then(
        (m) => m.EmissionFactorsFileDownloadComponent,
      ),
  },
  {
    path: 'upload',
    canMatch: [canExecuteSettingsEmissionFactors()],
    children: [
      {
        path: '',
        data: { pageTitle: 'Upload emission factors', breadcrumb: 'Upload emission factors', backlink: '..' },
        loadComponent: () =>
          import('./upload/upload-emission-factors.component').then((m) => m.UploadEmissionFactorsComponent),
      },
      {
        path: 'summary',
        data: { pageTitle: 'Check your answers', breadcrumb: 'Check your answers' },
        canActivate: [canActivateEmissionFactorsSummary],
        canDeactivate: [PendingRequestGuard],
        loadComponent: () =>
          import('./check-your-answers/emission-factors-check-your-answers.component').then(
            (m) => m.EmissionFactorsCheckYourAnswersComponent,
          ),
      },
    ],
  },
  {
    path: 'history',
    data: { pageTitle: 'Emission factors history', breadcrumb: 'Emission factors history' },
    loadComponent: () =>
      import('./history/emission-factors-history.component').then((m) => m.EmissionFactorsHistoryComponent),
  },
  {
    path: ':referenceYear',
    data: { pageTitle: 'Emission factors data', breadcrumb: 'Emission factors data', backlink: '..' },
    loadComponent: () => import('./data/emission-factors-data.component').then((m) => m.EmissionFactorsDataComponent),
  },
];
