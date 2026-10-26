import { EmissionFactorsNationalInventoryEntryDTO, EmissionFactorsRegionalNaturalGasEntryDTO } from 'pmrv-api';

const DISPLAYED_DECIMALS = 4;
const DECIMAL_PATTERN = /^(-?)(\d+)(?:\.(\d+))?$/;

/**
 * Rounds a decimal half up to the given number of decimals for display only, keeping the stored value untouched.
 * The digits are rounded as text so that values such as 1.000005 are not affected by floating point errors.
 */
export function roundHalfUp(value: string | number | null | undefined, decimals = DISPLAYED_DECIMALS): string {
  if (value === null || value === undefined) {
    // Missing values stay missing
    return value as null | undefined;
  }

  const text = String(value).trim();
  const match = DECIMAL_PATTERN.exec(text);
  if (!match || (match[3] ?? '').length <= decimals) {
    return text;
  }

  const [, sign, integerDigits, fractionDigits] = match;
  const truncated = BigInt(integerDigits + fractionDigits.slice(0, decimals));
  const rounded = Number(fractionDigits[decimals]) >= 5 ? truncated + BigInt(1) : truncated;
  const digits = rounded.toString().padStart(decimals + 1, '0');
  const fraction = decimals === 0 ? '' : stripTrailingZeros(digits.slice(-decimals));
  const integer = decimals === 0 ? digits : digits.slice(0, -decimals);
  const result = fraction ? `${integer}.${fraction}` : integer;

  return sign && /[1-9]/.test(result) ? `-${result}` : result;
}

function stripTrailingZeros(text: string): string {
  let end = text.length;
  while (end > 0 && text[end - 1] === '0') {
    end--;
  }
  return text.slice(0, end);
}

export function roundRegionalNaturalGasEntry(
  entry: EmissionFactorsRegionalNaturalGasEntryDTO,
): EmissionFactorsRegionalNaturalGasEntryDTO {
  return {
    ...entry,
    emissionFactor: roundHalfUp(entry.emissionFactor),
    netCalorificValue: roundHalfUp(entry.netCalorificValue),
  };
}

export function roundNationalInventoryEntry(
  entry: EmissionFactorsNationalInventoryEntryDTO,
): EmissionFactorsNationalInventoryEntryDTO {
  return {
    ...entry,
    emissionFactor: roundHalfUp(entry.emissionFactor),
    netCalorificValue: roundHalfUp(entry.netCalorificValue),
    oxidationFactor: roundHalfUp(entry.oxidationFactor),
  };
}
