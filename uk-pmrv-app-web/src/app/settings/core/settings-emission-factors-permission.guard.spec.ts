import { TestBed } from '@angular/core/testing';
import { Route, UrlSegment } from '@angular/router';

import { of, throwError } from 'rxjs';

import {
  canExecuteSettingsEmissionFactors,
  canViewSettingsEmissionFactors,
} from './settings-emission-factors-permission.guard';
import { SettingsEmissionFactorsPermissionService } from './settings-emission-factors-permission.service';

describe('settings-emission-factors-permission guards', () => {
  let settingsEmissionFactorsPermissionService: Partial<jest.Mocked<SettingsEmissionFactorsPermissionService>>;

  beforeEach(() => {
    settingsEmissionFactorsPermissionService = {
      canView: jest.fn().mockReturnValue(of(true)),
      canExecute: jest.fn().mockReturnValue(of(true)),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: SettingsEmissionFactorsPermissionService, useValue: settingsEmissionFactorsPermissionService },
      ],
    });
  });

  describe('canViewSettingsEmissionFactors', () => {
    it('allows the match when the user can view emission factors', (done) => {
      TestBed.runInInjectionContext(() => {
        const result = canViewSettingsEmissionFactors()({} as Route, [] as UrlSegment[]);

        (result as any).subscribe((value: boolean) => {
          expect(value).toEqual(true);
          done();
        });
      });
    });

    it('blocks the match when the user cannot view emission factors', (done) => {
      settingsEmissionFactorsPermissionService.canView.mockReturnValue(of(false));

      TestBed.runInInjectionContext(() => {
        const result = canViewSettingsEmissionFactors()({} as Route, [] as UrlSegment[]);

        (result as any).subscribe((value: boolean) => {
          expect(value).toEqual(false);
          done();
        });
      });
    });

    it('blocks the match when the backend call fails', (done) => {
      settingsEmissionFactorsPermissionService.canView.mockReturnValue(throwError(() => new Error('failed')));

      TestBed.runInInjectionContext(() => {
        const result = canViewSettingsEmissionFactors()({} as Route, [] as UrlSegment[]);

        (result as any).subscribe((value: boolean) => {
          expect(value).toEqual(false);
          done();
        });
      });
    });
  });

  describe('canExecuteSettingsEmissionFactors', () => {
    it('allows the match when the user can execute emission factors actions', (done) => {
      TestBed.runInInjectionContext(() => {
        const result = canExecuteSettingsEmissionFactors()({} as Route, [] as UrlSegment[]);

        (result as any).subscribe((value: boolean) => {
          expect(value).toEqual(true);
          done();
        });
      });
    });

    it('blocks the match when the user cannot execute emission factors actions', (done) => {
      settingsEmissionFactorsPermissionService.canExecute.mockReturnValue(of(false));

      TestBed.runInInjectionContext(() => {
        const result = canExecuteSettingsEmissionFactors()({} as Route, [] as UrlSegment[]);

        (result as any).subscribe((value: boolean) => {
          expect(value).toEqual(false);
          done();
        });
      });
    });

    it('blocks the match when the backend call fails', (done) => {
      settingsEmissionFactorsPermissionService.canExecute.mockReturnValue(throwError(() => new Error('failed')));

      TestBed.runInInjectionContext(() => {
        const result = canExecuteSettingsEmissionFactors()({} as Route, [] as UrlSegment[]);

        (result as any).subscribe((value: boolean) => {
          expect(value).toEqual(false);
          done();
        });
      });
    });
  });
});
