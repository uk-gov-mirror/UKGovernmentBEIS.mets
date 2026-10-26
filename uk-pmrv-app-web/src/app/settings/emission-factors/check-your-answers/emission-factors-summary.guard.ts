import { inject } from '@angular/core';
import { CanActivateFn, createUrlTreeFromSnapshot } from '@angular/router';

import { EmissionFactorsService } from '../emission-factors.service';

// The uploaded data is only held in memory until confirmation, so after a refresh the user has to upload again.
export const canActivateEmissionFactorsSummary: CanActivateFn = (route) =>
  !!inject(EmissionFactorsService).uploadedDetail() || createUrlTreeFromSnapshot(route, ['../']);
