import { inject } from '@angular/core';
import { CanMatchFn } from '@angular/router';

import { catchError, defaultIfEmpty, of } from 'rxjs';

import { SettingsEmissionFactorsPermissionService } from './settings-emission-factors-permission.service';

export function canViewSettingsEmissionFactors(): CanMatchFn {
  return () =>
    inject(SettingsEmissionFactorsPermissionService)
      .canView()
      .pipe(
        defaultIfEmpty(false),
        catchError(() => of(false)),
      );
}

export function canExecuteSettingsEmissionFactors(): CanMatchFn {
  return () =>
    inject(SettingsEmissionFactorsPermissionService)
      .canExecute()
      .pipe(
        defaultIfEmpty(false),
        catchError(() => of(false)),
      );
}
