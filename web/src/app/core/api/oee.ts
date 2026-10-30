import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { OeeResult } from '../models/oee';

@Service()
export class Oee {
  private readonly http = inject(HttpClient);

  /** `date` is the plant-local date as yyyy-MM-dd (see `plantDate`). */
  byShift(machineId: number, shiftCode: string, date: string): Observable<OeeResult> {
    return this.http.get<OeeResult>(`/api/machines/${machineId}/oee`, {
      params: {
        shiftCode,
        date
      }
    });
  }

  byRange(machineId: number, from: Date, to: Date): Observable<OeeResult> {
    return this.http.get<OeeResult>(`/api/machines/${machineId}/oee`, {
      params: {
        from: from.toISOString(),
        to: to.toISOString()
      }
    });
  }
}
