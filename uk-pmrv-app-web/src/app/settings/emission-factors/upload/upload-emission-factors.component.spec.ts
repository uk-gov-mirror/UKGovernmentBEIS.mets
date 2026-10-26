import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { of, throwError } from 'rxjs';

import { ActivatedRouteStub, BasePage } from '@testing';

import { EmissionFactorsService } from '../emission-factors.service';
import { UploadEmissionFactorsComponent } from './upload-emission-factors.component';

describe('UploadEmissionFactorsComponent', () => {
  let fixture: ComponentFixture<UploadEmissionFactorsComponent>;
  let page: Page;
  let router: Router;
  let route: ActivatedRouteStub;

  const currentYear = new Date().getFullYear();
  const validFileName = `uk-ggi-carbon-emission-factors-and-calorific-values-${currentYear}.xlsx`;

  const mockEmissionFactorsService = {
    uploadEmissionFactors: jest.fn().mockReturnValue(of(undefined)),
    getEmissionFactors: jest.fn().mockReturnValue(of([])),
  };

  class Page extends BasePage<UploadEmissionFactorsComponent> {
    set file(value: File) {
      this.setInputValue('input[type="file"]', value);
    }
    get errorSummary() {
      return this.query<HTMLDivElement>('.govuk-error-summary');
    }
    get errorSummaryList() {
      return Array.from(this.errorSummary.querySelectorAll('a')).map((anchor) => anchor.textContent.trim());
    }
    get submitButton() {
      return this.query<HTMLButtonElement>('button[type="submit"]');
    }
    get returnLink() {
      return this.queryAll('a').find((anchor) => anchor.textContent.trim() === 'Return to: Emission factors');
    }
    get warningText() {
      return this.query<HTMLElement>('.govuk-warning-text__text');
    }
  }

  function recreateComponent() {
    fixture = TestBed.createComponent(UploadEmissionFactorsComponent);
    page = new Page(fixture);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    route = new ActivatedRouteStub();
    mockEmissionFactorsService.uploadEmissionFactors.mockClear().mockReturnValue(of(undefined));
    mockEmissionFactorsService.getEmissionFactors.mockClear().mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [UploadEmissionFactorsComponent, RouterTestingModule],
      providers: [
        { provide: EmissionFactorsService, useValue: mockEmissionFactorsService },
        { provide: ActivatedRoute, useValue: route },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UploadEmissionFactorsComponent);
    page = new Page(fixture);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows a validation error when submitting without a file', () => {
    page.submitButton.click();
    fixture.detectChanges();

    expect(page.errorSummaryList).toEqual(['Select a file']);
  });

  it('shows a validation error when the file name is not for the current reporting year', () => {
    page.file = new File(['content'], 'uk-ggi-carbon-emission-factors-and-calorific-values-1999.xlsx');
    page.submitButton.click();
    fixture.detectChanges();

    expect(page.errorSummaryList).toEqual(['The selected file must be for the current reporting year']);
  });

  it('uploads the file and navigates to check your answers on success', () => {
    const navigateSpy = jest.spyOn(router, 'navigate');
    const file = new File(['content'], validFileName);

    page.file = file;
    page.submitButton.click();
    fixture.detectChanges();

    expect(page.errorSummary).toBeFalsy();
    expect(mockEmissionFactorsService.uploadEmissionFactors).toHaveBeenCalledWith(currentYear, file);
    expect(navigateSpy).toHaveBeenCalledWith(['summary'], { relativeTo: route });
  });

  it('stays on the upload page showing the backend error when the file is not for the current year', () => {
    const navigateSpy = jest.spyOn(router, 'navigate');
    mockEmissionFactorsService.uploadEmissionFactors.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'SETTINGS1001', message: 'The selected file must be for the current reporting year' },
          }),
      ),
    );

    page.file = new File(['content'], validFileName);
    page.submitButton.click();
    fixture.detectChanges();

    expect(page.errorSummaryList).toEqual(['The selected file must be for the current reporting year']);
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(page.submitButton.disabled).toBe(false);
  });

  it('shows the backend error message in the error summary when the upload is rejected', () => {
    mockEmissionFactorsService.uploadEmissionFactors.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'SETTINGS1006',
              message: 'The Regional natural gas emission factors table should only have 17 rows',
              data: [],
            },
          }),
      ),
    );

    page.file = new File(['content'], validFileName);
    page.submitButton.click();
    fixture.detectChanges();

    expect(page.errorSummaryList).toEqual(['The Regional natural gas emission factors table should only have 17 rows']);
  });

  it('shows a generic error in the error summary when the upload fails without a message', () => {
    mockEmissionFactorsService.uploadEmissionFactors.mockReturnValue(throwError(() => new Error('failed')));

    page.file = new File(['content'], validFileName);
    page.submitButton.click();
    fixture.detectChanges();

    expect(page.errorSummaryList).toEqual(['Sorry, there was a problem uploading the file. Try again later.']);
  });

  it('does not show the update message when no data exists for the current year', () => {
    expect(page.warningText).toBeNull();
  });

  describe('when the existing emission factors cannot be loaded', () => {
    beforeEach(() => {
      mockEmissionFactorsService.getEmissionFactors.mockReturnValue(throwError(() => new Error('unavailable')));
      recreateComponent();
    });

    it('still lets the user upload the file, without the update message', () => {
      const file = new File(['content'], validFileName);
      page.file = file;
      fixture.detectChanges();

      page.submitButton.click();
      fixture.detectChanges();

      expect(page.warningText).toBeNull();
      expect(mockEmissionFactorsService.uploadEmissionFactors).toHaveBeenCalledWith(currentYear, file);
    });
  });

  describe('when emission factors already exist for the current year', () => {
    beforeEach(() => {
      mockEmissionFactorsService.getEmissionFactors.mockReturnValue(
        of([{ referenceYear: currentYear, fileUuid: 'uuid', fileName: validFileName, uploadedAt: '2026-01-01' }]),
      );
      recreateComponent();
    });

    it('shows that uploading will update the existing data', () => {
      expect(page.warningText.textContent).toContain(
        'You are updating the emission factors. The current data for this year will be replaced.',
      );
    });
  });

  it('renders a working "Return to: Emission factors" link', () => {
    expect(page.returnLink).toBeTruthy();
    expect(page.returnLink.getAttribute('href')).toBeTruthy();
  });
});
