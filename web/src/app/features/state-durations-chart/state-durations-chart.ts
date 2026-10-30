import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { BarChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';
import { catchError, combineLatest, forkJoin, map, of, scan, switchMap, timer } from 'rxjs';
import { Machines } from '../../core/api/machines';
import { Oee } from '../../core/api/oee';
import { State } from '../../core/api/state';
import { Shift } from '../../core/models/shift';
import { formatPlantDateTime, latestShiftDate } from '../../shared/plant-time';
import { DurationsRow, durationsOptions } from './chart-options';

echarts.use([BarChart, GridComponent, LegendComponent, TooltipComponent, SVGRenderer]);

const REFRESH_MS = 60_000;
const BASE_HEIGHT_PX = 90;
const ROW_HEIGHT_PX = 44;

interface DurationsView {
  rows: DurationsRow[];
  /** ISO instants of the window the durations cover ('' before the first load). */
  from: string;
  to: string;
  /** At least one refresh succeeded. */
  loaded: boolean;
  /** The last refresh failed; the rows are from the previous one. */
  failed: boolean;
}

const INITIAL: DurationsView = { rows: [], from: '', to: '', loaded: false, failed: false };

@Component({
  imports: [NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts })],
  selector: 'app-state-durations-chart',
  styleUrl: './state-durations-chart.scss',
  templateUrl: './state-durations-chart.html',
})
export class StateDurationsChart {
  readonly lineId = input.required({ transform: numberAttribute });
  /** The shift to show; nothing is loaded while it is undefined. */
  readonly shift = input<Shift | undefined>();

  private readonly machinesApi = inject(Machines);
  private readonly oeeApi = inject(Oee);
  private readonly stateApi = inject(State);
  protected readonly formatPlantDateTime = formatPlantDateTime;

  /** Time per state of every machine of the line during the shift, refreshed every minute. */
  protected readonly view = toSignal(
    combineLatest([toObservable(this.lineId), toObservable(this.shift)]).pipe(
      switchMap(([lineId, shift]) =>
        shift === undefined
          ? of(INITIAL)
          : timer(0, REFRESH_MS).pipe(
              switchMap(() => this.load(lineId, shift)),
              scan(
                (prev, next): DurationsView =>
                  next ? { ...next, loaded: true, failed: false } : { ...prev, failed: true },
                INITIAL,
              ),
            ),
      ),
    ),
    { initialValue: INITIAL },
  );

  readonly options = computed(() => durationsOptions(this.view().rows));
  protected readonly heightPx = computed(() => BASE_HEIGHT_PX + ROW_HEIGHT_PX * this.view().rows.length);

  /**
   * The window of the shift comes from the MES (the OEE of the first machine reports the `from` and `to`
   * it used, with `to` clipped to now), so the shift rules live in one place.
   * null when the machines or that window could not be loaded.
   */
  private load(lineId: number, shift: Shift) {
    return this.machinesApi.listByLine(lineId).pipe(
      switchMap((page) => {
        const machines = page.content;
        if (machines.length === 0) {
          return of<Omit<DurationsView, 'loaded' | 'failed'>>({ rows: [], from: '', to: '' });
        }
        return this.oeeApi.byShift(machines[0].id, shift.code, latestShiftDate(shift.startTime)).pipe(
          switchMap((window) =>
            forkJoin(
              machines.map((machine) =>
                this.stateApi.durations(machine.id, new Date(window.from), new Date(window.to)).pipe(
                  map((durations): DurationsRow => ({ machine, durations })),
                  catchError(() => of<DurationsRow>({ machine, durations: null })),
                ),
              ),
            ).pipe(map((rows) => ({ rows, from: window.from, to: window.to }))),
          ),
        );
      }),
      catchError(() => of(null)),
    );
  }
}
