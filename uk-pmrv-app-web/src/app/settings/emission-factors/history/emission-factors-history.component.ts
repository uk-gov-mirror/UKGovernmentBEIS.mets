import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { switchMap } from 'rxjs';

import { PageHeadingComponent } from '@shared/page-heading/page-heading.component';
import { SharedModule } from '@shared/shared.module';

import {
  LinkDirective,
  SummaryListComponent,
  SummaryListRowDirective,
  SummaryListRowKeyDirective,
  SummaryListRowValueDirective,
} from 'govuk-components';

import { EmissionFactorsHistoryPage, EmissionFactorsService } from '../emission-factors.service';

@Component({
  selector: 'app-emission-factors-history',
  imports: [
    PageHeadingComponent,
    SharedModule,
    SummaryListComponent,
    SummaryListRowDirective,
    SummaryListRowKeyDirective,
    SummaryListRowValueDirective,
    LinkDirective,
    RouterLink,
  ],
  templateUrl: './emission-factors-history.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmissionFactorsHistoryComponent {
  private readonly emissionFactorsService = inject(EmissionFactorsService);

  readonly pageSize = 30;
  readonly page = signal(1);

  readonly history = toSignal(
    toObservable(this.page).pipe(
      switchMap((page) => this.emissionFactorsService.getEmissionFactorsHistory(page, this.pageSize)),
    ),
    { initialValue: { records: [], total: 0 } as EmissionFactorsHistoryPage },
  );

  onPageChange(page: number): void {
    this.page.set(page);
  }
}
