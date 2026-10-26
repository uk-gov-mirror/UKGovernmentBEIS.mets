import { roundHalfUp, roundNationalInventoryEntry, roundRegionalNaturalGasEntry } from './emission-factors.format';

describe('roundHalfUp', () => {
  it.each([
    ['93.8930718668602', '93.8931'],
    ['0.037392992823976', '0.0374'],
    ['1.23455', '1.2346'],
    ['1.234549999', '1.2345'],
    ['0.00005', '0.0001'],
    ['0.00004', '0'],
    ['9.99995', '10'],
    ['0.016200000000000', '0.0162'],
    ['100.00004', '100'],
    ['10.10004', '10.1'],
    ['-1.23455', '-1.2346'],
    ['-0.00004', '0'],
  ])('rounds %s half up to 4 decimals as %s', (value, expected) => {
    expect(roundHalfUp(value)).toEqual(expected);
  });

  it('rounds the value as text, unaffected by floating point errors', () => {
    expect(roundHalfUp('1.00005')).toEqual('1.0001');
    expect(roundHalfUp('2.67505')).toEqual('2.6751');
  });

  it.each([['29.8'], ['1.0'], ['56.5210'], ['42']])('keeps %s, which has 4 decimals or fewer, unchanged', (value) => {
    expect(roundHalfUp(value)).toEqual(value);
  });

  it.each([
    ['1.5', '2'],
    ['1.4', '1'],
    ['9.5', '10'],
    ['0.4', '0'],
    ['-1.5', '-2'],
  ])('rounds %s half up to 0 decimals as %s', (value, expected) => {
    expect(roundHalfUp(value, 0)).toEqual(expected);
  });

  it('accepts numbers', () => {
    expect(roundHalfUp(56.4234567)).toEqual('56.4235');
  });

  it('keeps values it cannot read as a plain decimal', () => {
    expect(roundHalfUp('1e-7')).toEqual('1e-7');
    expect(roundHalfUp('n/a')).toEqual('n/a');
  });

  it('keeps missing values', () => {
    expect(roundHalfUp(null)).toBeNull();
    expect(roundHalfUp(undefined)).toBeUndefined();
  });
});

describe('row rounding', () => {
  it('rounds the numeric regional natural gas values only', () => {
    expect(
      roundRegionalNaturalGasEntry({
        chargingZoneCode: 'EA',
        chargingZoneName: 'Eastern',
        emissionFactor: '56.5210000001',
        netCalorificValue: '0.037392992823976',
        calculationFactor: '0.9476',
      }),
    ).toEqual({
      chargingZoneCode: 'EA',
      chargingZoneName: 'Eastern',
      emissionFactor: '56.521',
      netCalorificValue: '0.0374',
      calculationFactor: '0.9476',
    });
  });

  it('rounds the numeric national inventory values only', () => {
    expect(
      roundNationalInventoryEntry({
        sectorName: '1A1a',
        ukGhgFuel: 'Coal',
        fuel: 'Coal',
        emissionFactor: '93.8930718668602',
        netCalorificValue: '23.62609594079182',
        oxidationFactor: '0.9827957983557349',
        ncvMeasurementUnit: 'GJ_PER_TONNE',
      }),
    ).toEqual({
      sectorName: '1A1a',
      ukGhgFuel: 'Coal',
      fuel: 'Coal',
      emissionFactor: '93.8931',
      netCalorificValue: '23.6261',
      oxidationFactor: '0.9828',
      ncvMeasurementUnit: 'GJ_PER_TONNE',
    });
  });
});
