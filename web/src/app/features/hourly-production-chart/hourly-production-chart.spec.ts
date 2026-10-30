import { NO_ERRORS_SCHEMA } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgxEchartsDirective } from 'ngx-echarts';
import { HourlyProductionChart } from './hourly-production-chart';

const MACHINES_URL = '/api/lines/3/machines';
const oeeUrl = (machineId: number) => `/api/machines/${machineId}/oee`;
const hourlyUrl = (machineId: number) => `/api/machines/${machineId}/production-counts/hourly`;

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

const hourly = (machineId: number, good: number) => ({
  machineId,
  from: FROM,
  to: TO,
  buckets: [{ bucketStart: FROM, goodCount: good, scrapCount: 1 }],
});

const robots = [
  { id: 7, code: 'ROBOT-01', name: 'Robot 1' },
  { id: 8, code: 'ROBOT-02', name: 'Robot 2' },
];

describe('HourlyProductionChart', () => {
  let fixture: ComponentFixture<HourlyProductionChart>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    // 14:00 in the plant (UTC-6)
    vi.setSystemTime(new Date('2026-10-28T20:00:00Z'));
    TestBed.configureTestingModule({
      imports: [HourlyProductionChart],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).overrideComponent(HourlyProductionChart, {
      // The real chart needs a browser; the options it receives are tested through `options()`.
      remove: { imports: [NgxEchartsDirective] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HourlyProductionChart);
    fixture.componentRef.setInput('lineId', 3);
    element = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function respondMachines(machines: unknown[]) {
    fixture.detectChanges();
    http.expectOne((r) => r.url === MACHINES_URL).flush({ content: machines, page: {} });
    fixture.detectChanges();
  }

  function show(s: unknown) {
    fixture.componentRef.setInput('shift', s);
    fixture.detectChanges();
    // The first poll fires on the next tick.
    vi.advanceTimersByTime(0);
  }

  /** Loads the machines, shows the shift and answers the OEE call that gives its window. */
  function load() {
    respondMachines(robots);
    show(shift('MORNING'));
    http.expectOne((r) => r.url === oeeUrl(7)).flush(window);
    fixture.detectChanges();
  }

  interface Opts {
    series: { name: string; data: number[] }[];
  }
  const good = () => (fixture.componentInstance.options() as unknown as Opts).series[0].data;

  it('loads nothing while there is no shift', () => {
    respondMachines(robots);
    vi.advanceTimersByTime(0);

    http.expectNone((r) => r.url === oeeUrl(7));
  });

  it('shows the hourly series of the first machine for the window the MES gave', () => {
    load();

    const req = http.expectOne((r) => r.url === hourlyUrl(7));
    expect(req.request.params.get('from')).toBe('2026-10-28T12:00:00.000Z');
    expect(req.request.params.get('to')).toBe('2026-10-28T20:00:00.000Z');
    req.flush(hourly(7, 50));
    fixture.detectChanges();

    expect(good()).toEqual([50]);
    expect(element.querySelector('.range')?.textContent).toContain('06:00');
  });

  it('asks the OEE for the start date of a night shift that began yesterday', () => {
    respondMachines(robots);
    show(shift('NIGHT', '22:00:00', '06:00:00'));

    const req = http.expectOne((r) => r.url === oeeUrl(7));
    expect(req.request.params.get('date')).toBe('2026-10-27');
    req.flush(window);
  });

  it('loads the picked machine', () => {
    load();
    http.expectOne((r) => r.url === hourlyUrl(7)).flush(hourly(7, 50));
    fixture.detectChanges();

    const select = element.querySelector('select') as HTMLSelectElement;
    select.value = '8';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    vi.advanceTimersByTime(0);

    http.expectOne((r) => r.url === oeeUrl(8)).flush({ ...window, machineId: 8 });
    http.expectOne((r) => r.url === hourlyUrl(8)).flush(hourly(8, 70));
    fixture.detectChanges();

    expect(good()).toEqual([70]);
  });

  it('says so when the line has no machines', () => {
    respondMachines([]);

    expect(element.textContent).toContain('no machines');
  });

  it('warns when the machines cannot be loaded', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === MACHINES_URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load the machines');
  });

  describe('refresh', () => {
    function loadOnce() {
      load();
      http.expectOne((r) => r.url === hourlyUrl(7)).flush(hourly(7, 50));
      fixture.detectChanges();
    }

    it('asks again every minute', () => {
      loadOnce();

      vi.advanceTimersByTime(60_000);
      http.expectOne((r) => r.url === oeeUrl(7)).flush(window);
      http.expectOne((r) => r.url === hourlyUrl(7)).flush(hourly(7, 60));
      fixture.detectChanges();

      expect(good()).toEqual([60]);
    });

    it('keeps the last data and warns when a refresh fails', () => {
      loadOnce();

      vi.advanceTimersByTime(60_000);
      http.expectOne((r) => r.url === oeeUrl(7)).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not refresh');
      expect(good()).toEqual([50]);
    });

    it('warns that the data could not be loaded when the first load fails', () => {
      load();
      http.expectOne((r) => r.url === hourlyUrl(7)).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load the hourly');
    });
  });
});
