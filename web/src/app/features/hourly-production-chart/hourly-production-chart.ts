import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { BarChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';
import { catchError, combineLatest, map, of, scan, switchMap, timer } from 'rxjs';
import { Machines } from '../../core/api/machines';
import { Oee } from '../../core/api/oee';
import { Production } from '../../core/api/production';
import { Machine } from '../../core/models/machine';
import { HourlyBucket } from '../../core/models/production';
import { Shift } from '../../core/models/shift';
import { formatPlantDateTime, latestShiftDate } from '../../shared/plant-time';
import { hourlyOptions } from './chart-options';

echarts.use([BarChart, GridComponent, LegendComponent, TooltipComponent, SVGRenderer]);

const REFRESH_MS = 60_000;

interface HourlyView {
  buckets: HourlyBucket[];
  /** ISO instants of the window the buckets cover ('' before the first load). */
  from: string;
  to: string;
  /** At least one refresh succeeded. */
  loaded: boolean;
  /** The last refresh failed; the buckets are from the previous one. */
  failed: boolean;
}

const INITIAL: HourlyView = { buckets: [], from: '', to: '', loaded: false, failed: false };

type MachinesState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; machines: Machine[] };

@Component({
  imports: [NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts })],
  selector: 'app-hourly-production-chart',
  styleUrl: './hourly-production-chart.scss',
  templateUrl: './hourly-production-chart.html',
})
export class HourlyProductionChart {
  readonly lineId = input.required({ transform: numberAttribute });
  /** The shift to show; nothing is loaded while it is undefined. */
  readonly shift = input<Shift | undefined>();

  private readonly machinesApi = inject(Machines);
  private readonly oeeApi = inject(Oee);
  private readonly productionApi = inject(Production);
  protected readonly formatPlantDateTime = formatPlantDateTime;

  private readonly machinesState = toSignal(
    toObservable(this.lineId).pipe(
      switchMap((lineId) =>
        this.machinesApi.listByLine(lineId).pipe(
          map((page): MachinesState => ({ status: 'ready', machines: page.content })),
          catchError(() => of<MachinesState>({ status: 'error' })),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as MachinesState },
  );

  protected readonly machines = computed(() => {
    const state = this.machinesState();
    return state.status === 'ready' ? state.machines : [];
  });
  protected readonly machinesFailed = computed(() => this.machinesState().status === 'error');

  /** What the user picked; it may not exist in the current line's machines. */
  private readonly pickedId = signal(0);

  /** The picked machine if the line has it, otherwise the first one (undefined when there are none). */
  protected readonly machine = computed(() => {
    const machines = this.machines();
    return machines.find((m) => m.id === this.pickedId()) ?? machines[0];
  });

  protected pick(id: string): void {
    this.pickedId.set(Number(id));
  }

  /** Good and scrap pieces per hour of the machine during the shift, refreshed every minute. */
  protected readonly view = toSignal(
    combineLatest([toObservable(this.machine), toObservable(this.shift)]).pipe(
      switchMap(([machine, shift]) =>
        machine === undefined || shift === undefined
          ? of(INITIAL)
          : timer(0, REFRESH_MS).pipe(
              switchMap(() => this.load(machine, shift)),
              scan(
                (prev, next): HourlyView =>
                  next ? { ...next, loaded: true, failed: false } : { ...prev, failed: true },
                INITIAL,
              ),
            ),
      ),
    ),
    { initialValue: INITIAL },
  );

  readonly options = computed(() => hourlyOptions(this.view().buckets));

  /**
   * The window of the shift comes from the MES (the OEE reports the `from` and `to` it used,
   * with `to` clipped to now), so the shift rules live in one place.
   * null when that window or the series could not be loaded.
   */
  private load(machine: Machine, shift: Shift) {
    return this.oeeApi.byShift(machine.id, shift.code, latestShiftDate(shift.startTime)).pipe(
      switchMap((window) =>
        this.productionApi
          .hourly(machine.id, new Date(window.from), new Date(window.to))
          .pipe(map((series) => ({ buckets: series.buckets, from: window.from, to: window.to }))),
      ),
      catchError(() => of(null)),
    );
  }
}
