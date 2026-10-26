import BigNumber from 'bignumber.js';

import {
  CalculationEmissionCalculationParamValues,
  CalculationOfCO2Emissions,
  CalculationSourceStreamEmission,
  MeasurementCO2EmissionPointEmission,
  MeasurementN2OEmissionPointEmission,
  PfcSourceStreamEmission,
} from 'pmrv-api';

function areTransferredWizardStepsFilled(
  emission: CalculationSourceStreamEmission | MeasurementCO2EmissionPointEmission | MeasurementN2OEmissionPointEmission,
  hasTransfer: boolean,
): boolean {
  return (
    !hasTransfer ||
    emission?.transfer?.entryAccountingForTransfer === false ||
    !!emission?.transfer?.installationDetails ||
    !!emission?.transfer?.installationEmitter
  );
}

export function isCalculationWizardComplete(
  sourceStreamEmission: CalculationSourceStreamEmission,
  hasTransfer: boolean,
): boolean {
  const calculationCorrect = (sourceStreamEmission?.parameterCalculationMethod as any)?.emissionCalculationParamValues
    ?.calculationCorrect;

  return (
    calculationCorrect !== undefined &&
    calculationCorrect !== null &&
    areTransferredWizardStepsFilled(sourceStreamEmission, hasTransfer)
  );
}

export function isMeasurementWizardComplete(
  emissionPointEmission: MeasurementCO2EmissionPointEmission | MeasurementN2OEmissionPointEmission,
  hasTransfer: boolean,
): boolean {
  const calculationCorrect = emissionPointEmission?.calculationCorrect;

  return (
    calculationCorrect !== undefined &&
    calculationCorrect !== null &&
    areTransferredWizardStepsFilled(emissionPointEmission, hasTransfer)
  );
}

export function isPfcWizardComplete(sourceStreamEmission: PfcSourceStreamEmission): boolean {
  const calculationCorrect = sourceStreamEmission?.calculationCorrect;

  return calculationCorrect !== undefined && calculationCorrect !== null;
}

export function getCalculationSourceStreamEmissionValues(
  sourceStreamEmission: CalculationSourceStreamEmission,
  hasTransfer: boolean,
  isReview: boolean,
): [number | string, number | string] {
  const emissionCalculationParamValues: CalculationEmissionCalculationParamValues =
    sourceStreamEmission?.parameterCalculationMethod?.emissionCalculationParamValues;

  let [reportableEmissions, biomassEmissions]: [number | string, number | string] =
    isReview || isCalculationWizardComplete(sourceStreamEmission, hasTransfer)
      ? [
          emissionCalculationParamValues?.providedEmissions?.totalProvidedReportableEmissions ??
            (emissionCalculationParamValues?.totalReportableEmissions || 0),
          emissionCalculationParamValues?.providedEmissions?.totalProvidedSustainableBiomassEmissions ??
            (emissionCalculationParamValues?.totalSustainableBiomassEmissions || 0),
        ]
      : [0, 0];

  if (hasTransfer && !!sourceStreamEmission?.transfer?.entryAccountingForTransfer) {
    if (sourceStreamEmission?.transfer?.transferDirection === 'RECEIVED_FROM_ANOTHER_INSTALLATION') {
      reportableEmissions = new BigNumber(reportableEmissions).abs().toString();
      biomassEmissions = new BigNumber(biomassEmissions).abs().toString();
    } else {
      reportableEmissions = new BigNumber(reportableEmissions).abs().negated().toString();
      biomassEmissions = new BigNumber(biomassEmissions).abs().negated().toString();
    }
  }

  return [reportableEmissions, biomassEmissions];
}

export function getCalculationTotalReportableEmissions(
  calculation: CalculationOfCO2Emissions,
  isReview = false,
): BigNumber {
  return (calculation?.sourceStreamEmissions ?? []).reduce(
    (total, sourceStreamEmission) =>
      total.plus(
        getCalculationSourceStreamEmissionValues(sourceStreamEmission, !!calculation.hasTransfer, isReview)[0],
      ),
    new BigNumber(0),
  );
}

export function isCalculationTotalReportableEmissionsNegative(calculation: CalculationOfCO2Emissions): boolean {
  return getCalculationTotalReportableEmissions(calculation).isLessThan(0);
}
