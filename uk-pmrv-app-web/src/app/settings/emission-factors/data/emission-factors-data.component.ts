import { ChangeDetectionStrategy, Component, computed, inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { PageHeadingComponent } from '@shared/page-heading/page-heading.component';

import {
  ButtonDirective,
  GovukTableColumn,
  LinkDirective,
  SummaryListComponent,
  SummaryListRowDirective,
  SummaryListRowKeyDirective,
  SummaryListRowValueDirective,
  TableComponent,
  WarningTextComponent,
} from 'govuk-components';

import { EmissionFactorsNationalInventoryEntryDTO, EmissionFactorsRegionalNaturalGasEntryDTO } from 'pmrv-api';

import { SettingsEmissionFactorsPermissionService } from '../../core/settings-emission-factors-permission.service';
import { NATIONAL_INVENTORY_COLUMNS, NCV_UNIT_LABELS, REGIONAL_COLUMNS } from '../emission-factors.columns';
import { roundNationalInventoryEntry, roundRegionalNaturalGasEntry } from '../emission-factors.format';
import { EmissionFactorsService } from '../emission-factors.service';

interface ViewModel {
  referenceYear: number;
  fileName: string | undefined;
  regionalColumns: GovukTableColumn<EmissionFactorsRegionalNaturalGasEntryDTO>[];
  regionalRows: EmissionFactorsRegionalNaturalGasEntryDTO[];
  nationalInventoryColumns: GovukTableColumn<EmissionFactorsNationalInventoryEntryDTO>[];
  nationalInventoryRows: EmissionFactorsNationalInventoryEntryDTO[];
  ncvUnitLabels: Record<string, string>;
  canUpdate: boolean;
}

@Component({
  selector: 'app-emission-factors-data',
  imports: [
    PageHeadingComponent,
    WarningTextComponent,
    ButtonDirective,
    SummaryListComponent,
    SummaryListRowDirective,
    SummaryListRowKeyDirective,
    SummaryListRowValueDirective,
    TableComponent,
    LinkDirective,
    RouterLink,
  ],
  templateUrl: './emission-factors-data.component.html',
  styleUrl: './emission-factors-data.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmissionFactorsDataComponent {
  private readonly emissionFactorsService = inject(EmissionFactorsService);
  private readonly referenceYear = Number(inject(ActivatedRoute).snapshot.paramMap.get('referenceYear'));
  private readonly detail = toSignal(this.emissionFactorsService.getEmissionFactorsDetail(this.referenceYear));
  // The detail carries no file metadata, so the uploaded file is taken from the year's entry.
  private readonly entries = toSignal(this.emissionFactorsService.getEmissionFactors());
  private readonly canExecute = toSignal(inject(SettingsEmissionFactorsPermissionService).canExecute(), {
    initialValue: false,
  });

  readonly vm: Signal<ViewModel | null> = computed(() => {
    const detail = this.detail();
    const entries = this.entries();

    return detail && entries
      ? {
          referenceYear: this.referenceYear,
          fileName: entries.find((entry) => entry.referenceYear === this.referenceYear)?.fileName,
          regionalColumns: REGIONAL_COLUMNS,
          regionalRows: detail.regionalNaturalGas.map(roundRegionalNaturalGasEntry),
          nationalInventoryColumns: NATIONAL_INVENTORY_COLUMNS,
          nationalInventoryRows: detail.nationalInventory.map(roundNationalInventoryEntry),
          ncvUnitLabels: NCV_UNIT_LABELS,
          canUpdate: this.canExecute() && this.referenceYear === new Date().getFullYear(),
        }
      : null;
  });
}
