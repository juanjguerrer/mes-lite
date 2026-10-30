import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { Shift } from '../models/shift';

@Service()
export class Shifts {
  private readonly http = inject(HttpClient);

  listByLine(lineId: number): Observable<Shift[]> {
    return this.http.get<Shift[]>(`/api/lines/${lineId}/shifts`);
  }
}
