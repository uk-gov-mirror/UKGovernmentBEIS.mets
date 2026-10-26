import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';

import {
  getCalculationSourceStreamEmissionValues,
  getCalculationTotalReportableEmissions,
} from '@shared/components/approaches/aer/monitoring-approaches.functions';
import { SourceStreamDescriptionPipe } from '@shared/pipes/source-streams-description.pipe';
import { statusMap } from '@shared/task-list/task-item/status.map';
import { TaskItemStatus } from '@shared/task-list/task-list.interface';
import BigNumber from 'bignumber.js';

import { GovukTableColumn } from 'govuk-components';

import { Aer, CalculationOfCO2Emissions } from 'pmrv-api';

interface TemplateCalculationSourceStreamEmission {
  sourceStreamDescription?: string;
  emissionSources?: string[];
  reportableEmissions?: number | string;
  biomassEmissions?: number | string;
  status?: string;
}

@Component({
  selector: 'app-calculation-emissions-group',
  standalone: false,
  templateUrl: './calculation-emissions-group.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalculationEmissionsGroupComponent implements OnInit {
  @Input() data: Aer;
  @Input() statuses: TaskItemStatus[];
  @Input() isReview = true;

  sourceStreamEmissions: TemplateCalculationSourceStreamEmission[];

  statusMap = statusMap;

  columns: GovukTableColumn<TemplateCalculationSourceStreamEmission>[] = [
    { field: 'sourceStreamDescription', header: 'Source stream' },
    { field: 'emissionSources', header: 'Emission sources' },
    { field: 'reportableEmissions', header: 'Reportable emissions' },
    { field: 'biomassEmissions', header: 'Sustainable biomass' },
    { field: 'status', header: '' },
  ];

  ngOnInit(): void {
    const monitoringApproachEmissions = this.data.monitoringApproachEmissions;

    const hasTransfer = !!(monitoringApproachEmissions.CALCULATION_CO2 as CalculationOfCO2Emissions)?.hasTransfer;

    const currentSourceStreamEmissions = (monitoringApproachEmissions.CALCULATION_CO2 as CalculationOfCO2Emissions)
      ?.sourceStreamEmissions;
    const aerSourceStreams = this.data.sourceStreams;
    const aerEmissionSources = this.data.emissionSources;

    this.sourceStreamEmissions = [...(currentSourceStreamEmissions ?? [])]?.map((sourceStreamEmission, index) => {
      const sourceStreamDescriptionPipe = new SourceStreamDescriptionPipe();
      const aerSourceStream = aerSourceStreams.find(
        (sourceStream) => sourceStream.id === sourceStreamEmission.sourceStream,
      );

      const sourceStreamDescription = aerSourceStream?.reference
        ? `${aerSourceStream.reference} ${sourceStreamDescriptionPipe.transform(aerSourceStream)}`
        : null;

      const emissionSources = sourceStreamEmission.emissionSources?.map(
        (currentEmissionSource) =>
          aerEmissionSources.find((emissionSource) => emissionSource.id === currentEmissionSource)?.reference,
      );

      const [reportableEmissions, biomassEmissions] = getCalculationSourceStreamEmissionValues(
        sourceStreamEmission,
        hasTransfer,
        this.isReview,
      );

      return {
        sourceStreamDescription,
        emissionSources,
        reportableEmissions: reportableEmissions,
        biomassEmissions: biomassEmissions,
        status: this.isReview ? '' : this.statuses[index],
      };
    });

    if (this.sourceStreamEmissions.length) {
      const sumOfBiomassEmissions = this.sourceStreamEmissions.reduce(
        (sum, sourceStreamEmission) => sum.plus(sourceStreamEmission.biomassEmissions),
        new BigNumber(0),
      );

      this.sourceStreamEmissions.push({
        sourceStreamDescription: 'totals',
        emissionSources: [''],
        reportableEmissions: getCalculationTotalReportableEmissions(
          monitoringApproachEmissions.CALCULATION_CO2 as CalculationOfCO2Emissions,
          this.isReview,
        ).toString(),
        biomassEmissions: sumOfBiomassEmissions.toString(),
        status: '',
      });
    }
  }
}
