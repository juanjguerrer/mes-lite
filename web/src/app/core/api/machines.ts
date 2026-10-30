import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { Machine } from '../models/machine';
import { Page } from '../models/page';

@Service()
export class Machines {
  private readonly http = inject(HttpClient);

  listByLine(lineId: number): Observable<Page<Machine>> {
    return this.http.get<Page<Machine>>(`/api/lines/${lineId}/machines`, {
      params: {
        size: 100
      }
    });
  }
}
