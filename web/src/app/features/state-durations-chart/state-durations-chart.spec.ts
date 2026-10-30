import { NO_ERRORS_SCHEMA } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgxEchartsDirective } from 'ngx-echarts';
import { StateDurationsChart } from './state-durations-chart';

const MACHINES_URL = '/api/lines/3/machines';
const oeeUrl = (machineId: number) => `/api/machines/${machineId}/oee`;
const durationsUrl = (machineId: number) => `/api/machines/${machineId}/state-durations`;

const FROM = '2026-10-28T12:00:00Z';
const TO = '2026-10-28T20:00:00Z';

const shift = (code: string, startTime = '06:00:00', endTime = '14:00:00') => ({
  id: 1,
  lineId: 3,
  code,
  name: `Shift ${code}`,
  startTime,
  endTime,
  crossesMidnight: endTime <= startTime,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

const window = { machineId: 7, from: FROM, to: TO, warnings: [] };

const durations = (running: number) => ({
  machineId: 7,
  from: FROM,
  to: TO,
  durationsMs: { RUNNING: running, IDLE: 0, DOWN: 0, SETUP: 0, PLANNED_STOP: 0 },
  unknownMs: 0,
});

const robots = [
  { id: 7, code: 'ROBOT-01' },
  { id: 8, code: 'ROBOT-02' },
];

describe('StateDurationsChart', () => {
  let fixture: ComponentFixture<StateDurationsChart>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    // 14:00 in the plant (UTC-6)
    vi.setSystemTime(new Date('2026-10-28T20:00:00Z'));
    TestBed.configureTestingModule({
      imports: [StateDurationsChart],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).overrideComponent(StateDurationsChart, {
      // The real chart needs a browser; the options it receives are tested through `options()`.
      remove: { imports: [NgxEchartsDirective] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StateDurationsChart);
    fixture.componentRef.setInput('lineId', 3);
    element = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function show(s: unknown) {
    fixture.componentRef.setInput('shift', s);
    fixture.detectChanges();
    // The first poll fires on the next tick.
    vi.advanceTimersByTime(0);
  }

  function respondMachines(machines: unknown[]) {
    http.expectOne((r) => r.url === MACHINES_URL).flush({ content: machines, page: {} });
    fixture.detectChanges();
  }

  /** Answers the OEE call that gives the window of the shift, then the durations calls. */
  function load() {
    show(shift('MORNING'));
    respondMachines(robots);
    http.expectOne((r) => r.url === oeeUrl(7)).flush(window);
    fixture.detectChanges();
  }

  interface Opts {
    series: { name: string; data: number[] }[];
  }
  const running = () => (fixture.componentInstance.options() as unknown as Opts).series.find((s) => s.name === 'Running');

  it('loads nothing while there is no shift', () => {
    fixture.detectChanges();
    vi.advanceTimersByTime(0);

    http.expectNone(MACHINES_URL);
  });

  it('asks the durations of every machine for the window the MES gave for the shift', () => {
    load();

    const first = http.expectOne((r) => r.url === durationsUrl(7));
    const second = http.expectOne((r) => r.url === durationsUrl(8));
    expect(first.request.params.get('from')).toBe('2026-10-28T12:00:00.000Z');
    expect(first.request.params.get('to')).toBe('2026-10-28T20:00:00.000Z');
    first.flush(durations(60 * 60_000));
    second.flush(durations(30 * 60_000));
    fixture.detectChanges();

    expect(running()?.data).toEqual([60, 30]);
    expect(element.querySelector('.range')?.textContent).toContain('06:00');
  });

  it('asks the OEE for the start date of a night shift that began yesterday', () => {
    show(shift('NIGHT', '22:00:00', '06:00:00'));
    respondMachines([robots[0]]);

    const req = http.expectOne((r) => r.url === oeeUrl(7));
    expect(req.request.params.get('date')).toBe('2026-10-27');
    req.flush(window);
  });

  it('keeps the other machines when one fails', () => {
    load();

    http.expectOne((r) => r.url === durationsUrl(7)).flush(null, { status: 500, statusText: 'Server Error' });
    http.expectOne((r) => r.url === durationsUrl(8)).flush(durations(30 * 60_000));
    fixture.detectChanges();

    expect(running()?.data).toEqual([0, 30]);
  });

  it('says so when the line has no machines', () => {
    show(shift('MORNING'));
    respondMachines([]);

    expect(element.textContent).toContain('no machines');
  });

  describe('refresh', () => {
    function loadOnce() {
      show(shift('MORNING'));
      respondMachines([robots[0]]);
      http.expectOne((r) => r.url === oeeUrl(7)).flush(window);
      http.expectOne((r) => r.url === durationsUrl(7)).flush(durations(60 * 60_000));
      fixture.detectChanges();
    }

    it('asks again every minute', () => {
      loadOnce();

      vi.advanceTimersByTime(60_000);
      http.expectOne((r) => r.url === MACHINES_URL).flush({ content: [robots[0]], page: {} });
      http.expectOne((r) => r.url === oeeUrl(7)).flush(window);
      http.expectOne((r) => r.url === durationsUrl(7)).flush(durations(0));
    });

    it('keeps the last data and warns when a refresh fails', () => {
      loadOnce();

      vi.advanceTimersByTime(60_000);
      http.expectOne((r) => r.url === MACHINES_URL).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not refresh');
      expect(running()?.data).toEqual([60]);
    });

    it('warns that the data could not be loaded when the first load fails', () => {
      show(shift('MORNING'));
      http.expectOne((r) => r.url === MACHINES_URL).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load');
    });
  });
});
