import { Component, inject, input, numberAttribute } from '@angular/core';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { switchMap, map, catchError, of, timer, scan } from 'rxjs';
import { State } from '../../core/api/state';
import { MachineCurrentState, MachineStateColors, MachineStateLabels } from '../../core/models/machine-state';
import { formatPlantTime } from '../../shared/plant-time';

interface MachinesView {
  machines: MachineCurrentState[];
  loaded: boolean;
  failed: boolean;
}

const INITIAL: MachinesView = { machines: [], loaded: false, failed: false };

@Component({
  imports: [],
  selector: 'app-machine-states',
  styleUrl: './machine-states.scss',
  templateUrl: './machine-states.html',
})
export class MachineStates {
  readonly lineId = input.required({ transform: numberAttribute });
  private readonly stateApi = inject(State);
  protected readonly formatTime = formatPlantTime;
  protected readonly colors = MachineStateColors;
  protected readonly labels = MachineStateLabels;
  /** The current state of the line. */
  protected readonly view = toSignal(
    toObservable(this.lineId).pipe(
      switchMap((lineId) =>
        timer(0, 5000).pipe(
          switchMap(() =>
            this.stateApi.currentByLine(lineId).pipe(
              map((machines) => [...machines].sort((a, b) => a.machineId - b.machineId)),
              catchError(() => of(null)),
            ),
          ),
          scan((prev, next): MachinesView =>
            next ? { machines: next, loaded: true, failed: false }
              : { ...prev, failed: true },
              INITIAL
          )
        ),
      ),
    ), { initialValue: INITIAL }
  );
}
