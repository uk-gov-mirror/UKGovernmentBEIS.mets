import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { catchError, map, of } from 'rxjs';

import { isBadRequest } from '@error/business-errors';
import { PageHeadingComponent } from '@shared/page-heading/page-heading.component';

import {
  ButtonDirective,
  ErrorSummaryComponent,
  FileUploadComponent,
  GovukValidators,
  LinkDirective,
  WarningTextComponent,
} from 'govuk-components';

import { EmissionFactorsService } from '../emission-factors.service';
import { currentReportingYearFileNameValidator } from './upload-emission-factors.validators';

const MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;

@Component({
  selector: 'app-upload-emission-factors',
  imports: [
    ReactiveFormsModule,
    PageHeadingComponent,
    ErrorSummaryComponent,
    FileUploadComponent,
    ButtonDirective,
    LinkDirective,
    WarningTextComponent,
    RouterLink,
  ],
  templateUrl: './upload-emission-factors.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UploadEmissionFactorsComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly emissionFactorsService = inject(EmissionFactorsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly currentYear = new Date().getFullYear();

  /** Whether emission factors were already added for the year, in which case the upload updates them. */
  readonly isUpdate = toSignal(
    this.emissionFactorsService.getEmissionFactors().pipe(
      map((entries) => entries.some((entry) => entry.referenceYear === this.currentYear)),
      // The lookup only decides whether to show the update message, so a failure must not block the upload
      catchError(() => of(false)),
    ),
    { initialValue: false },
  );

  readonly form = this.formBuilder.group({
    file: [
      null as FileList | null,
      [
        GovukValidators.required('Select a file'),
        GovukValidators.fileExtension('xlsx', 'The selected file must be an XLSX'),
        GovukValidators.maxFileSize(MAX_FILE_SIZE_BYTES, 'The selected file must be smaller than 20MB'),
        currentReportingYearFileNameValidator(this.currentYear),
      ],
    ],
  });

  readonly isErrorSummaryDisplayed = signal(false);
  readonly isSubmitting = signal(false);

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.isErrorSummaryDisplayed.set(true);
      return;
    }

    this.isErrorSummaryDisplayed.set(false);
    this.isSubmitting.set(true);

    const file = this.form.controls.file.value![0];

    this.emissionFactorsService.uploadEmissionFactors(this.currentYear, file).subscribe({
      next: () => this.router.navigate(['summary'], { relativeTo: this.route }),
      error: (res: unknown) => {
        this.isSubmitting.set(false);
        this.form.controls.file.setErrors({ uploadFailed: this.getUploadErrorMessage(res) });
        this.form.markAllAsTouched();
        this.isErrorSummaryDisplayed.set(true);
      },
    });
  }

  private getUploadErrorMessage(res: unknown): string {
    return (
      (isBadRequest(res) && res.error?.message) || 'Sorry, there was a problem uploading the file. Try again later.'
    );
  }
}
