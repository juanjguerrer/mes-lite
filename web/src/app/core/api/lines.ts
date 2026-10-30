import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models/page';
import { Line } from '../models/line';

@Service()
export class Lines {
  private readonly http = inject(HttpClient);

  list(): Observable<Page<Line>> {
    return this.http.get<Page<Line>>('/api/lines', {
      params: {
        size: 100
      }
    });
  }
}
