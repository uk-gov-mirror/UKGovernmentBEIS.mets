import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { DetailsComponent } from 'govuk-components';

@Component({
  selector: 'app-include-calc-measurement-info',
  imports: [DetailsComponent],
  templateUrl: './include-calc-measurement-info.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncludeCalcMeasurementInfoComponent {
  isMeasurement = input<boolean>(false);
}
