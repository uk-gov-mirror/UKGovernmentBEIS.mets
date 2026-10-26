import { mockAerApplyPayload } from '@tasks/aer/submit/testing/mock-aer-apply-action';

import { AerApplicationSubmitRequestTaskPayload, CalculationOfCO2Emissions } from 'pmrv-api';

import { getSourceStreamsStatus } from './emissions-status';

describe('getSourceStreamsStatus', () => {
  const buildPayload = (transferDirection: string): AerApplicationSubmitRequestTaskPayload => {
    const payload = JSON.parse(JSON.stringify(mockAerApplyPayload)) as AerApplicationSubmitRequestTaskPayload;
    const calculation = payload.aer.monitoringApproachEmissions.CALCULATION_CO2 as CalculationOfCO2Emissions;

    calculation.sourceStreamEmissions = calculation.sourceStreamEmissions.slice(0, 1);
    calculation.sourceStreamEmissions[0].transfer.transferDirection = transferDirection as any;
    payload.aerSectionsCompleted = {
      ...payload.aerSectionsCompleted,
      sourceStreams: [true],
      emissionSources: [true],
      CALCULATION_CO2: [true],
    };

    return payload;
  };

  it('should return needs review when calculation total reportable emissions are negative', () => {
    expect(getSourceStreamsStatus('CALCULATION_CO2', buildPayload('EXPORTED_TO_LONG_TERM_FACILITY'))).toEqual(
      'needs review',
    );
  });

  it('should return complete when calculation total reportable emissions are not negative', () => {
    expect(getSourceStreamsStatus('CALCULATION_CO2', buildPayload('RECEIVED_FROM_ANOTHER_INSTALLATION'))).toEqual(
      'complete',
    );
  });
});
