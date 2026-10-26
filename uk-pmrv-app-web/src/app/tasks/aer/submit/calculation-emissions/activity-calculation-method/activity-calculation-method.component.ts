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

import { PendingRequestService } from '../../../../../core/guards/pending-request.service';
import { AerService } from '../../../core/aer.service';
import { getCompletionStatus } from '../../../shared/components/submit/emissions-status';
import {
  AER_CALCULATION_EMISSIONS_FORM,
  buildParameterCalculationMethodData,
  buildTaskData,
} from '../calculation-emissions';
import { activityCalculationMethodFormProvider } from './activity-calculation-method.provider';

@Component({
  selector: 'app-activity-calculation-method',
  standalone: false,
  templateUrl: './activity-calculation-method.component.html',
  providers: [activityCalculationMethodFormProvider],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityCalculationMethodComponent {
  index$ = this.route.paramMap.pipe(map((paramMap) => Number(paramMap.get('index'))));
  payload$ = this.aerService.getPayload();

  isEditable$ = this.aerService.isEditable$;

  constructor(
    @Inject(AER_CALCULATION_EMISSIONS_FORM) readonly form: UntypedFormGroup,
    readonly aerService: AerService,
    private readonly router: Router,
    readonly sourceStreamDescriptionPipe: SourceStreamDescriptionPipe,
    readonly route: ActivatedRoute,
    readonly pendingRequest: PendingRequestService,
  ) {}

  sourceStreamEmission$: Observable<CalculationSourceStreamEmission> = combineLatest([this.payload$, this.index$]).pipe(
    map(([payload, index]) => {
      return (payload.aer.monitoringApproachEmissions.CALCULATION_CO2 as CalculationOfCO2Emissions)
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
              getCompletionStatus('CALCULATION_CO2', payload, index, false),
              'CALCULATION_CO2',
            ),
          ),

          this.pendingRequest.trackRequest(),
        )
        .subscribe(() => this.navigateNext());
    }
  }

  private buildData(payload: AerApplicationSubmitRequestTaskPayload, index: number) {
    const monitoringApproachEmissions = payload.aer.monitoringApproachEmissions;
    const calculation = monitoringApproachEmissions.CALCULATION_CO2 as CalculationOfCO2Emissions;

    const parameterCalculationMethodData = {
      calculationActivityDataCalculationMethod: {
        type: this.form.get('type').value,
      },
    };

    const parameterCalculationMethod = buildParameterCalculationMethodData(
      calculation.sourceStreamEmissions?.[index],
      parameterCalculationMethodData,
    );

    const sourceStreamEmissions = calculation.sourceStreamEmissions.map((item, idx) =>
      idx === index
        ? {
            ...item,
            ...parameterCalculationMethod,
          }
        : item,
    );

    const data = buildTaskData(payload, sourceStreamEmissions);

    return data;
  }

  private navigateNext() {
    const nextStep =
      this.form.get('type').value === 'AGGREGATION_OF_METERING_QUANTITIES'
        ? '../activity-calculation-aggregation'
        : '../activity-calculation-continuous';

    this.router.navigate([`${nextStep}`], { relativeTo: this.route });
  }
}
