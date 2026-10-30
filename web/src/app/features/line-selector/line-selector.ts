import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, filter, map, of, startWith } from 'rxjs';
import { Lines } from '../../core/api/lines';
import { Line } from '../../core/models/line';

type LinesState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; lines: Line[] };

@Component({
  imports: [],
  selector: 'app-line-selector',
  styleUrl: './line-selector.scss',
  templateUrl: './line-selector.html',
})
export class LineSelector {
  private readonly router = inject(Router);

  private readonly state = toSignal(
    inject(Lines)
      .list()
      .pipe(
        map((page): LinesState => ({ status: 'ready', lines: page.content })),
        catchError(() => of<LinesState>({ status: 'error' })),
      ),
    { initialValue: { status: 'loading' } as LinesState },
  );

  protected readonly lines = computed(() => {
    const state = this.state();
    return state.status === 'ready' ? state.lines : [];
  });
  protected readonly loading = computed(() => this.state().status === 'loading');
  protected readonly failed = computed(() => this.state().status === 'error');

  /** The `lineId` of the current URL, or '' when no line is selected. */
  protected readonly selectedId = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.router.routerState.snapshot.root.firstChild?.paramMap.get('lineId') ?? ''),
    ),
    { requireSync: true },
  );

  protected select(lineId: string): void {
    void this.router.navigate(lineId ? ['/lines', lineId] : ['/']);
  }
}
