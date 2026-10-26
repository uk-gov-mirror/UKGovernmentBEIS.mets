import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { combineLatest, first, map, Observable, switchMap } from 'rxjs';

import { SourceStreamDescriptionPipe } from '@shared/pipes/source-streams-description.pipe';

import {
  AerApplicationSubmitRequestTaskPayload,
  CalculationOfCO2Emissions,
  CalculationSourceStreamEmission,
} from 'pmrv-api';

import { PendingRequestService } from '../../../../../../core/guards/pending-request.service';
import { AerService } from '../../../../core/aer.service';
import { AER_APPROACHES_FORM, buildTaskData } from '../emissions';
import { getCompletionStatus } from '../emissions-status';
import { dataRangeFormProvider } from './date-range.provider';

@Component({
  selector: 'app-date-range',
  standalone: false,
  templateUrl: './date-range.component.html',
  providers: [dataRangeFormProvider],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DateRangeComponent {
  index$ = this.route.paramMap.pipe(map((paramMap) => Number(paramMap.get('index'))));
  payload$ = this.aerService.getPayload();

  taskKey = this.route.snapshot.data.taskKey;
  isEditable$ = this.aerService.isEditable$;

  constructor(
    @Inject(AER_APPROACHES_FORM) readonly form: UntypedFormGroup,
    readonly aerService: AerService,
    private readonly router: Router,
    readonly route: ActivatedRoute,
    readonly sourceStreamDescriptionPipe: SourceStreamDescriptionPipe,
    readonly pendingRequest: PendingRequestService,
  ) {}

  sourceStreamEmission$: Observable<CalculationSourceStreamEmission> = combineLatest([this.payload$, this.index$]).pipe(
    map(([payload, index]) => {
      return (payload.aer.monitoringApproachEmissions?.[this.taskKey] as CalculationOfCO2Emissions)
        ?.sourceStreamEmissions?.[index];
    }),
  );

  sourceStream$ = combineLatest([this.payload$, this.sourceStreamEmission$]).pipe(
    map(([payload, sourceStreamEmission]) => {
      const usedSourceStream = payload.aer.sourceStreams.find(
        (sourceStream) => sourceStream.id === sourceStreamEmission.sourceStream,
      );

      return `${usedSourceStream.reference} ${this.sourceStreamDescriptionPipe.transform(usedSourceStream)}`;
    }),
  );

  onContinue(): void {
    if (!this.form.dirty) {
      this.navigateNext();
    } else {
      combineLatest([this.payload$, this.index$])
        .pipe(
          first(),
          switchMap(([payload, index]) =>
            this.aerService.postTaskSave(
              this.buildData(payload, index),
              undefined,
              getCompletionStatus(this.taskKey, payload, index, false),
              this.taskKey,
            ),
          ),
          this.pendingRequest.trackRequest(),
        )
        .subscribe(() => this.navigateNext());
    }
  }

  private buildData(payload: AerApplicationSubmitRequestTaskPayload, index: number) {
    const monitoringApproachEmissions = payload.aer.monitoringApproachEmissions;
    const approach = monitoringApproachEmissions[this.taskKey] as any;

    const fullYearCovered = this.form.get('fullYearCovered').value;

    const durationRange = {
      durationRange: {
        fullYearCovered: fullYearCovered,
        ...(!fullYearCovered
          ? {
              coverageStartDate: this.form.get('coverageStartDate').value,
              coverageEndDate: this.form.get('coverageEndDate').value,
            }
          : {}),
      },
    };

    const sourceStreamEmissions = approach.sourceStreamEmissions.map((item, idx) =>
      idx === index
        ? {
            ...item,
            ...durationRange,
          }
        : item,
    );

    return buildTaskData(this.taskKey, payload, sourceStreamEmissions);
  }

  private navigateNext() {
    const nextStep = '../tiers-used';

    this.router.navigate([`${nextStep}`], { relativeTo: this.route });
  }
}
