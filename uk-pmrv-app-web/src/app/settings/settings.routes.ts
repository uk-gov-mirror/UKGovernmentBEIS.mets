import { Routes } from '@angular/router';

import { canViewSettingsEmissionFactors } from './core/settings-emission-factors-permission.guard';
import { canViewSettingsFees } from './core/settings-fees-permission.guard';
import { SettingsComponent } from './settings.component';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    data: { pageTitle: 'Settings' },
    component: SettingsComponent,
  },
  {
    path: 'emission-factors',
    canMatch: [canViewSettingsEmissionFactors()],
    data: { pageTitle: 'Settings', breadcrumb: 'Emission factors' },
    loadChildren: () => import('./emission-factors/emission-factors.routes').then((m) => m.EMISSION_FACTORS_ROUTES),
  },
  {
    path: 'fees',
    canMatch: [canViewSettingsFees()],
    data: { pageTitle: 'Settings', breadcrumb: 'Fees' },
    loadChildren: () => import('./fees/fees.routes').then((m) => m.FEES_ROUTES),
  },
];
