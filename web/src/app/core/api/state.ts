import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { MachineCurrentState } from '../models/machine-state';
import { StateDurations } from '../models/state-durations';

@Service()
export class State {
  private readonly http = inject(HttpClient);

  currentByLine(lineId: number): Observable<MachineCurrentState[]> {
    return this.http.get<MachineCurrentState[]>(`/api/lines/${lineId}/machines/states`);
  }

  durations(machineId: number, from: Date, to: Date): Observable<StateDurations> {
    return this.http.get<StateDurations>(`/api/machines/${machineId}/state-durations`, {
      params: {
        from: from.toISOString(),
        to: to.toISOString()
      }
    });
  }
}
