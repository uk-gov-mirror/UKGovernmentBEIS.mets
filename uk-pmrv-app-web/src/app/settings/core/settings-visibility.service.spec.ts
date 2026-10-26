import { TestBed } from '@angular/core/testing';

import { firstValueFrom, of } from 'rxjs';

import { SettingsService } from 'pmrv-api';

import { SettingsEmissionFactorsPermissionService } from './settings-emission-factors-permission.service';
import { SettingsFeesPermissionService } from './settings-fees-permission.service';
import { SettingsVisibilityService } from './settings-visibility.service';

describe('SettingsVisibilityService', () => {
  let service: SettingsVisibilityService;
  let settingsService: Partial<jest.Mocked<SettingsService>>;
  let settingsEmissionFactorsPermissionService: Partial<jest.Mocked<SettingsEmissionFactorsPermissionService>>;
  let settingsFeesPermissionService: Partial<jest.Mocked<SettingsFeesPermissionService>>;

  function setup(sections: string[], canViewFees: boolean, canViewEmissionFactors = false) {
    settingsService = { getAccessibleSections: jest.fn().mockReturnValue(of(sections)) };
    settingsEmissionFactorsPermissionService = { canView: jest.fn().mockReturnValue(of(canViewEmissionFactors)) };
    settingsFeesPermissionService = { canView: jest.fn().mockReturnValue(of(canViewFees)) };

    TestBed.configureTestingModule({
      providers: [
        { provide: SettingsService, useValue: settingsService },
        { provide: SettingsEmissionFactorsPermissionService, useValue: settingsEmissionFactorsPermissionService },
        { provide: SettingsFeesPermissionService, useValue: settingsFeesPermissionService },
      ],
    });

    service = TestBed.inject(SettingsVisibilityService);
  }

  it('is false when there are no accessible sections', async () => {
    setup([], false);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(false);
  });

  it('is false when FEES is accessible but the user cannot view fees', async () => {
    setup(['FEES'], false);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(false);
    expect(settingsFeesPermissionService.canView).toHaveBeenCalled();
  });

  it('is true when FEES is accessible and the user can view fees', async () => {
    setup(['FEES'], true);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(true);
  });

  it('does not check fees permission when FEES is not an accessible section', async () => {
    setup(['EMISSION_FACTORS'], false, true);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(true);
    expect(settingsFeesPermissionService.canView).not.toHaveBeenCalled();
  });

  it('is false when EMISSION_FACTORS is accessible but the user cannot view emission factors', async () => {
    setup(['EMISSION_FACTORS'], false, false);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(false);
    expect(settingsEmissionFactorsPermissionService.canView).toHaveBeenCalled();
  });

  it('is true when EMISSION_FACTORS is accessible and the user can view emission factors', async () => {
    setup(['EMISSION_FACTORS'], false, true);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(true);
  });

  it('does not check emission factors permission when EMISSION_FACTORS is not an accessible section', async () => {
    setup(['FEES'], true, false);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('INSTALLATION'))).toEqual(true);
    expect(settingsEmissionFactorsPermissionService.canView).not.toHaveBeenCalled();
  });

  it('is true when GLOBAL_WARMING_POTENTIALS is accessible', async () => {
    setup(['GLOBAL_WARMING_POTENTIALS'], false);

    expect(await firstValueFrom(service.hasAnyAccessibleSection('AVIATION'))).toEqual(true);
  });

  it('requests the accessible sections for the given account type', async () => {
    setup(['FEES'], true);

    await firstValueFrom(service.hasAnyAccessibleSection('AVIATION'));

    expect(settingsService.getAccessibleSections).toHaveBeenCalledWith('AVIATION');
  });
});
