import { AbstractControl, ValidatorFn } from '@angular/forms';

import { GovukValidators, MessageValidatorFn } from 'govuk-components';

export function currentReportingYearFileNameValidator(currentYear: number): MessageValidatorFn {
  return GovukValidators.builder(
    'The selected file must be for the current reporting year',
    fileNameMatchesYear(currentYear),
  );
}

function fileNameMatchesYear(year: number): ValidatorFn {
  const expectedName = `uk-ggi-carbon-emission-factors-and-calorific-values-${year}.xlsx`;

  return (control: AbstractControl) => {
    const file = (control.value as FileList | null)?.[0];

    return file && file.name.toLowerCase() !== expectedName.toLowerCase() ? { fileYear: true } : null;
  };
}
