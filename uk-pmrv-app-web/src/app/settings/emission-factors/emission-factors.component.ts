import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, Signal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { PageHeadingComponent } from '@shared/page-heading/page-heading.component';

import {
  ButtonDirective,
  GovukTableColumn,
  LinkDirective,
  NotificationBannerComponent,
  SortEvent,
  TableComponent,
} from 'govuk-components';

import { EmissionFactorsHistoryEntryDTO } from 'pmrv-api';

import { SettingsEmissionFactorsPermissionService } from '../core/settings-emission-factors-permission.service';
import { EmissionFactorsService } from './emission-factors.service';

interface ViewModel {
  rows: EmissionFactorsHistoryEntryDTO[];
  columns: GovukTableColumn<EmissionFactorsHistoryEntryDTO>[];
  canExecute: boolean;
}

const COLUMNS: GovukTableColumn<EmissionFactorsHistoryEntryDTO>[] = [
  { field: 'referenceYear', header: 'Reference year', isSortable: true, isHeader: true },
  { field: 'fileName', header: 'File' },
  { field: 'uploadedAt', header: 'Last updated' },
];

@Component({
  selector: 'app-emission-factors',
  imports: [
    PageHeadingComponent,
    TableComponent,
    LinkDirective,
    ButtonDirective,
    NotificationBannerComponent,
    RouterLink,
    DatePipe,
  ],
  templateUrl: './emission-factors.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmissionFactorsComponent {
  private readonly emissionFactorsService = inject(EmissionFactorsService);
  private readonly permissionService = inject(SettingsEmissionFactorsPermissionService);

  private readonly emissionFactors = toSignal(this.emissionFactorsService.getEmissionFactors(), {
    initialValue: [] as EmissionFactorsHistoryEntryDTO[],
  });
  private readonly canExecute = toSignal(this.permissionService.canExecute(), { initialValue: false });
  private readonly sorting = signal<SortEvent>({ column: 'referenceYear', direction: 'descending' });

  readonly notification: string | undefined = inject(Router).currentNavigation()?.extras.state?.notification;

  readonly vm: Signal<ViewModel> = computed(() => ({
    rows: this.emissionFactors().slice().sort(this.sortRows(this.sorting())),
    columns: COLUMNS,
    canExecute: this.canExecute(),
  }));

  onSort(event: SortEvent): void {
    this.sorting.set(event);
  }

  private sortRows(
    sorting: SortEvent,
  ): (a: EmissionFactorsHistoryEntryDTO, b: EmissionFactorsHistoryEntryDTO) => number {
    return (a, b) => (a.referenceYear - b.referenceYear) * (sorting.direction === 'ascending' ? 1 : -1);
  }
}
