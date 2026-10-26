import { inject, Injectable, signal } from '@angular/core';

import { map, Observable, tap, throwError } from 'rxjs';

import {
  EmissionFactorsDetailDTO,
  EmissionFactorsHistoryEntryDTO,
  EmissionFactorsHistoryRecordDTO,
  SettingsService,
} from 'pmrv-api';

import { formatFeeHistoryDateTime } from '../fees/fee-history-date';

export interface UploadedEmissionFactorsDetail {
  referenceYear: number;
  file: File;
  fileUuid: string;
  detail: EmissionFactorsDetailDTO;
}

export interface EmissionFactorsHistoryRecord {
  changedAtDisplay: string;
  changedBy: string;
  referenceYear: number;
}

export interface EmissionFactorsHistoryPage {
  records: EmissionFactorsHistoryRecord[];
  total: number;
}

@Injectable({ providedIn: 'root' })
export class EmissionFactorsService {
  private readonly settingsService = inject(SettingsService);

  /** The parsed result of the last upload, kept for check your answers until the pending upload is submitted. */
  private readonly _uploadedDetail = signal<UploadedEmissionFactorsDetail | null>(null);
  readonly uploadedDetail = this._uploadedDetail.asReadonly();

  getEmissionFactors(): Observable<EmissionFactorsHistoryEntryDTO[]> {
    return this.settingsService.getEmissionFactors();
  }

  getEmissionFactorsHistory(page: number, size = 30): Observable<EmissionFactorsHistoryPage> {
    return this.settingsService.getEmissionFactorsHistory(page - 1, size).pipe(
      map((response) => ({
        records: (response.history ?? []).map(toEmissionFactorsHistoryRecord),
        total: response.totalItems ?? 0,
      })),
    );
  }

  getEmissionFactorsDetail(referenceYear: number): Observable<EmissionFactorsDetailDTO> {
    return this.settingsService.getEmissionFactorsDetail(referenceYear);
  }

  uploadEmissionFactors(referenceYear: number, file: File): Observable<EmissionFactorsDetailDTO> {
    // A failed re-upload must not leave the previous file reachable from check your answers.
    this._uploadedDetail.set(null);

    return this.settingsService.uploadEmissionFactors(referenceYear, file).pipe(
      tap((upload) =>
        this._uploadedDetail.set({ referenceYear, file, fileUuid: upload.fileUuid, detail: upload.data }),
      ),
      map((upload) => upload.data),
    );
  }

  submitEmissionFactors(): Observable<void> {
    const uploaded = this._uploadedDetail();
    if (!uploaded) {
      return throwError(() => new Error('There is no uploaded emission factors file to submit'));
    }

    return this.settingsService
      .submitEmissionFactors(uploaded.fileUuid)
      .pipe(tap(() => this._uploadedDetail.set(null)));
  }
}

function toEmissionFactorsHistoryRecord(dto: EmissionFactorsHistoryRecordDTO): EmissionFactorsHistoryRecord {
  return {
    changedAtDisplay: formatFeeHistoryDateTime(dto.changedAt),
    changedBy: dto.changedBy,
    referenceYear: dto.referenceYear,
  };
}
