import { GovukTableColumn } from 'govuk-components';

import { EmissionFactorsNationalInventoryEntryDTO, EmissionFactorsRegionalNaturalGasEntryDTO } from 'pmrv-api';

export const REGIONAL_COLUMNS: GovukTableColumn<EmissionFactorsRegionalNaturalGasEntryDTO>[] = [
  { field: 'chargingZoneName', header: 'Local Distribution Zone (LDZ)', isHeader: true },
  { field: 'chargingZoneCode', header: 'LDZ abbreviation' },
  { field: 'emissionFactor', header: 'Emission factor (tCO2/TJ net)' },
  { field: 'netCalorificValue', header: 'Net calorific value (GJ/Nm³)' },
];

export const NATIONAL_INVENTORY_COLUMNS: GovukTableColumn<EmissionFactorsNationalInventoryEntryDTO>[] = [
  { field: 'sectorName', header: 'CRT category', isHeader: true },
  { field: 'ukGhgFuel', header: 'UK GHG fuel' },
  { field: 'netCalorificValue', header: 'Net calorific value', isNumeric: true },
  { field: 'ncvMeasurementUnit', header: 'Unit' },
  { field: 'oxidationFactor', header: 'Oxidation factor', isNumeric: true },
];

export const NCV_UNIT_LABELS: Record<EmissionFactorsNationalInventoryEntryDTO['ncvMeasurementUnit'], string> = {
  GJ_PER_TONNE: 'GJ/tonne',
  GJ_PER_NM3: 'GJ/m³',
};
