import {
  Component,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  output,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { Shifts } from '../../core/api/shifts';
import { Shift } from '../../core/models/shift';
import { formatShiftTime } from '../../shared/plant-clock';

type ShiftsState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; shifts: Shift[] };

@Component({
  imports: [],
  selector: 'app-shift-selector',
  styleUrl: './shift-selector.scss',
  templateUrl: './shift-selector.html',
})
export class ShiftSelector {
  readonly lineId = input.required({ transform: numberAttribute });

  /** The selected shift; undefined while loading and when the line has no shifts. */
  readonly shiftChange = output<Shift | undefined>();

  private readonly shiftsApi = inject(Shifts);
  protected readonly formatShiftTime = formatShiftTime;

  private readonly shiftsState = toSignal(
    toObservable(this.lineId).pipe(
      switchMap((lineId) =>
        this.shiftsApi.listByLine(lineId).pipe(
          map((shifts): ShiftsState => ({ status: 'ready', shifts })),
          catchError(() => of<ShiftsState>({ status: 'error' })),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as ShiftsState },
  );

  protected readonly shifts = computed(() => {
    const state = this.shiftsState();
    return state.status === 'ready' ? state.shifts : [];
  });
  protected readonly failed = computed(() => this.shiftsState().status === 'error');

  /** What the user picked; it may not exist in the current line's shifts. */
  private readonly pickedCode = signal('');

  /** The picked shift if the line has it, otherwise the first one (undefined when there are none). */
  protected readonly shift = computed(() => {
    const shifts = this.shifts();
    return shifts.find((s) => s.code === this.pickedCode()) ?? shifts[0];
  });

  constructor() {
    effect(() => this.shiftChange.emit(this.shift()));
  }

  protected pick(code: string): void {
    this.pickedCode.set(code);
  }
}
