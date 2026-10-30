import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { HourlyProduction } from '../models/production';

@Service()
export class Production {
  private readonly http = inject(HttpClient);

  hourly(machineId: number, from: Date, to: Date): Observable<HourlyProduction> {
    return this.http.get<HourlyProduction>(`/api/machines/${machineId}/production-counts/hourly`, {
      params: {
        from: from.toISOString(),
        to: to.toISOString()
      }
    });
  }
}
