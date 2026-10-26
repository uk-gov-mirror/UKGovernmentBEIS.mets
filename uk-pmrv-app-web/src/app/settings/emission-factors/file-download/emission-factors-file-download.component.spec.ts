import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';

import { defer, firstValueFrom, of, take } from 'rxjs';

import { ActivatedRouteStub } from '@testing';

import { FileAttachmentsService, SettingsService } from 'pmrv-api';

import { testSchedulerFactory } from '../../../../testing/marble-helpers';
import { EmissionFactorsFileDownloadComponent } from './emission-factors-file-download.component';

describe('EmissionFactorsFileDownloadComponent', () => {
  let component: EmissionFactorsFileDownloadComponent;
  let fixture: ComponentFixture<EmissionFactorsFileDownloadComponent>;

  const settingsService = { generateGetEmissionFactorsFileToken: jest.fn() };

  beforeEach(async () => {
    Object.defineProperty(window, 'onfocus', { set: jest.fn() });
    settingsService.generateGetEmissionFactorsFileToken
      .mockReset()
      .mockReturnValue(of({ token: 'abce', tokenExpirationMinutes: 1 }));

    await TestBed.configureTestingModule({
      imports: [EmissionFactorsFileDownloadComponent],
      providers: [
        { provide: ActivatedRoute, useValue: new ActivatedRouteStub({ referenceYear: '2025' }) },
        { provide: SettingsService, useValue: settingsService },
        { provide: FileAttachmentsService, useValue: { configuration: { basePath: '' } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmissionFactorsFileDownloadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('requests a token for the reference year in the route', () => {
    expect(settingsService.generateGetEmissionFactorsFileToken).toHaveBeenCalledWith(2025);
  });

  it('should display the download link', async () => {
    await expect(firstValueFrom(component.url$)).resolves.toEqual('/v1.0/file-attachments/abce');
  });

  it('should refresh the download link', () => {
    settingsService.generateGetEmissionFactorsFileToken.mockReset().mockImplementation(() => {
      let subscribes = 0;

      return defer(() => {
        subscribes += 1;

        return subscribes === 1
          ? of({ token: 'abcf', tokenExpirationMinutes: 1 })
          : subscribes === 2
            ? of({ token: 'abcd', tokenExpirationMinutes: 2 })
            : of({ token: 'abce', tokenExpirationMinutes: 1 });
      });
    });

    testSchedulerFactory().run(({ expectObservable }) =>
      expectObservable(component.url$.pipe(take(3))).toBe('a 59s 999ms b 119s 999ms (c|)', {
        a: '/v1.0/file-attachments/abcf',
        b: '/v1.0/file-attachments/abcd',
        c: '/v1.0/file-attachments/abce',
      }),
    );
  });
});
