import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, Signal, signal } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { catchError, map, of, switchMap } from 'rxjs';

import { PendingRequestService } from '@core/guards/pending-request.service';
import { isBadRequest } from '@error/business-errors';
import { PageHeadingComponent } from '@shared/page-heading/page-heading.component';

import {
  ButtonDirective,
  ErrorSummaryComponent,
  GovukTableColumn,
  LinkDirective,
  SummaryListComponent,
  SummaryListRowActionsDirective,
  SummaryListRowDirective,
  SummaryListRowKeyDirective,
  SummaryListRowValueDirective,
  TableComponent,
} from 'govuk-components';

import { EmissionFactorsNationalInventoryEntryDTO, EmissionFactorsRegionalNaturalGasEntryDTO } from 'pmrv-api';

import { NATIONAL_INVENTORY_COLUMNS, NCV_UNIT_LABELS, REGIONAL_COLUMNS } from '../emission-factors.columns';
import { roundNationalInventoryEntry, roundRegionalNaturalGasEntry } from '../emission-factors.format';
import { EmissionFactorsService } from '../emission-factors.service';

interface ViewModel {
  fileName: string;
  regionalColumns: GovukTableColumn<EmissionFactorsRegionalNaturalGasEntryDTO>[];
  regionalRows: EmissionFactorsRegionalNaturalGasEntryDTO[];
  nationalInventoryColumns: GovukTableColumn<EmissionFactorsNationalInventoryEntryDTO>[];
  nationalInventoryRows: EmissionFactorsNationalInventoryEntryDTO[];
  ncvUnitLabels: Record<string, string>;
}

@Component({
  selector: 'app-emission-factors-check-your-answers',
  imports: [
    PageHeadingComponent,
    ErrorSummaryComponent,
    SummaryListComponent,
    SummaryListRowDirective,
    SummaryListRowKeyDirective,
    SummaryListRowValueDirective,
    SummaryListRowActionsDirective,
    TableComponent,
    ButtonDirective,
    LinkDirective,
    RouterLink,
  ],
  templateUrl: './emission-factors-check-your-answers.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmissionFactorsCheckYourAnswersComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly emissionFactorsService = inject(EmissionFactorsService);
  private readonly pendingRequest = inject(PendingRequestService);

  readonly form = inject(FormBuilder).group({});
  readonly isErrorSummaryDisplayed = signal(false);
  readonly isSubmitting = signal(false);
  readonly fileUrl: string;

  readonly vm: Signal<ViewModel | null> = computed(() => {
    const uploaded = this.emissionFactorsService.uploadedDetail();

    return uploaded
      ? {
          fileName: uploaded.file.name,
          regionalColumns: REGIONAL_COLUMNS,
          regionalRows: uploaded.detail.regionalNaturalGas.map(roundRegionalNaturalGasEntry),
          nationalInventoryColumns: NATIONAL_INVENTORY_COLUMNS,
          nationalInventoryRows: uploaded.detail.nationalInventory.map(roundNationalInventoryEntry),
          ncvUnitLabels: NCV_UNIT_LABELS,
        }
      : null;
  });

  constructor() {
    // The file is not stored on the server until confirmation, so it is downloaded from the local copy.
    const fileUrl = URL.createObjectURL(this.emissionFactorsService.uploadedDetail().file);
    this.fileUrl = fileUrl;
    inject(DestroyRef).onDestroy(() => URL.revokeObjectURL(fileUrl));
  }

  onConfirm(): void {
    this.isErrorSummaryDisplayed.set(false);
    this.isSubmitting.set(true);

    const { referenceYear } = this.emissionFactorsService.uploadedDetail();

    // Whether the year already had emission factors is checked when confirming, i.e. right before they are replaced
    this.emissionFactorsService
      .getEmissionFactors()
      .pipe(
        map((entries) => entries.some((entry) => entry.referenceYear === referenceYear)),
        // The lookup only decides the wording of the success message, so a failure must not block the submission
        catchError(() => of(false)),
        switchMap((isUpdate) => this.emissionFactorsService.submitEmissionFactors().pipe(map(() => isUpdate))),
        this.pendingRequest.trackRequest(),
      )
      .subscribe({
        next: (isUpdate) =>
          this.router.navigate(['../..'], {
            relativeTo: this.route,
            state: {
              notification: `Emission factors for ${referenceYear} have been ${isUpdate ? 'updated' : 'added'}.`,
            },
          }),
        error: (res: unknown) => {
          this.isSubmitting.set(false);
          this.form.setErrors({ submitFailed: this.getSubmitErrorMessage(res) });
          this.isErrorSummaryDisplayed.set(true);
        },
      });
  }

  private getSubmitErrorMessage(res: unknown): string {
    return (
      (isBadRequest(res) && res.error?.message) ||
      'Sorry, there was a problem submitting the emission factors. Try again later.'
    );
  }
}
