import { Component, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, forkJoin, map, of, scan, switchMap, timer } from 'rxjs';
import { Machines } from '../../core/api/machines';
import { Oee } from '../../core/api/oee';
import { Machine } from '../../core/models/machine';
import { OeeResult } from '../../core/models/oee';
import { Shift } from '../../core/models/shift';
import { formatRatio } from '../../shared/format-ratio';
import { latestShiftDate } from '../../shared/plant-time';

const REFRESH_MS = 30_000;

interface OeeRow {
  machine: Machine;
  /** null when the OEE call of this machine failed. */
  oee: OeeResult | null;
}

interface OeeView {
  /** The shift's start date (yyyy-MM-dd) the rows were computed for. */
  date: string;
  rows: OeeRow[];
  /** At least one refresh succeeded. */
  loaded: boolean;
  /** The last refresh failed; rows are from the previous one. */
  failed: boolean;
}

const INITIAL: OeeView = { date: '', rows: [], loaded: false, failed: false };

@Component({
  imports: [],
  selector: 'app-oee-table',
  styleUrl: './oee-table.scss',
  templateUrl: './oee-table.html',
})
export class OeeTable {
  readonly lineId = input.required({ transform: numberAttribute });
  /** The shift to show; nothing is loaded while it is undefined. */
  readonly shift = input<Shift | undefined>();

  private readonly machinesApi = inject(Machines);
  private readonly oeeApi = inject(Oee);
  protected readonly formatRatio = formatRatio;

  /** The OEE of every machine of the line for the shift, refreshed every 30 s. */
  protected readonly view = toSignal(
    combineLatest([toObservable(this.lineId), toObservable(this.shift)]).pipe(
      switchMap(([lineId, shift]) =>
        shift === undefined
          ? of(INITIAL)
          : timer(0, REFRESH_MS).pipe(
              switchMap(() => this.load(lineId, shift)),
              scan(
                (prev, next): OeeView =>
                  next ? { ...next, loaded: true, failed: false } : { ...prev, failed: true },
                INITIAL,
              ),
            ),
      ),
    ),
    { initialValue: INITIAL },
  );

  /** null when the machines of the line could not be loaded. */
  private load(lineId: number, shift: Shift) {
    const date = latestShiftDate(shift.startTime);
    return this.machinesApi.listByLine(lineId).pipe(
      switchMap((page) =>
        // forkJoin never emits for an empty array
        page.content.length === 0
          ? of<OeeRow[]>([])
          : forkJoin(
              page.content.map((machine) =>
                this.oeeApi.byShift(machine.id, shift.code, date).pipe(
                  map((oee): OeeRow => ({ machine, oee })),
                  catchError(() => of<OeeRow>({ machine, oee: null })),
                ),
              ),
            ),
      ),
      map((rows) => ({ date, rows })),
      catchError(() => of(null)),
    );
  }
}
