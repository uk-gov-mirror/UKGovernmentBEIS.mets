import { ChangeDetectionStrategy, Component } from '@angular/core';

import { LinkDirective } from 'govuk-components';

@Component({
  selector: 'app-activity-data-info',
  imports: [LinkDirective],
  templateUrl: './include-activity-data-info.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncludeActivityDataInfoComponent {}
