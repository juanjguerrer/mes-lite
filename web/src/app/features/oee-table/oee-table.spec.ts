import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OeeTable } from './oee-table';

const SHIFTS_URL = '/api/lines/3/shifts';
const MACHINES_URL = '/api/lines/3/machines';
const oeeUrl = (machineId: number) => `/api/machines/${machineId}/oee`;

const shift = (id: number, code: string, startTime = '06:00:00', endTime = '14:00:00') => ({
  id,
  lineId: 3,
  code,
  name: `Shift ${code}`,
  startTime,
  endTime,
  crossesMidnight: endTime <= startTime,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

const oee = (overrides = {}) => ({
  availability: 1,
  performance: 1,
  quality: 1,
  oee: 1,
  warnings: [],
  ...overrides,
});

const robots = [
  { id: 7, code: 'ROBOT-01' },
  { id: 8, code: 'ROBOT-02' },
];

describe('OeeTable', () => {
  let fixture: ComponentFixture<OeeTable>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    // 14:00 in the plant (UTC-6)
    vi.setSystemTime(new Date('2026-10-28T20:00:00Z'));
    TestBed.configureTestingModule({
      imports: [OeeTable],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OeeTable);
    fixture.componentRef.setInput('lineId', 3);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function respondShifts(shifts: unknown[]) {
    http.expectOne(SHIFTS_URL).flush(shifts);
    fixture.detectChanges();
    // The first poll fires on the next tick.
    vi.advanceTimersByTime(0);
  }

  function respondMachines(machines: unknown[]) {
    http.expectOne((r) => r.url === MACHINES_URL).flush({ content: machines, page: {} });
    fixture.detectChanges();
  }

  const cells = (row: Element) => [...row.querySelectorAll('td')].map((e) => e.textContent?.trim());

  describe('shift selector', () => {
    it('lists the shifts of the line with their hours', () => {
      respondShifts([shift(1, 'MORNING'), shift(2, 'NIGHT')]);

      const options = [...element.querySelectorAll('option')].map((o) => o.textContent?.trim());
      expect(options).toHaveLength(2);
      expect(options[0]).toContain('MORNING');
      expect(options[0]).toContain('06:00–14:00');
    });

    it('selects the first shift by default and keeps a later pick', () => {
      respondShifts([shift(1, 'MORNING'), shift(2, 'NIGHT')]);
      const select = element.querySelector('select') as HTMLSelectElement;
      expect(select.value).toBe('MORNING');

      select.value = 'NIGHT';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(select.value).toBe('NIGHT');
    });

    it('says so when the line has no shifts', () => {
      respondShifts([]);

      expect(element.querySelector('select')).toBeNull();
      expect(element.textContent).toContain('no shifts');
    });

    it('warns when the shifts cannot be loaded', () => {
      http.expectOne(SHIFTS_URL).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load the shifts');
    });
  });

  describe('OEE rows', () => {
    it('shows one row per machine with its OEE for the picked shift', () => {
      respondShifts([shift(1, 'MORNING')]);
      respondMachines(robots);

      const first = http.expectOne((r) => r.url === oeeUrl(7));
      expect(first.request.params.get('shiftCode')).toBe('MORNING');
      expect(first.request.params.get('date')).toBe('2026-10-28');
      first.flush(
        oee({ availability: 0.8734, performance: null, oee: null, warnings: ['No ideal cycle time'] }),
      );
      http.expectOne((r) => r.url === oeeUrl(8)).flush(oee({ availability: 0.5, quality: 0.5, oee: 0.125 }));
      fixture.detectChanges();

      const rows = element.querySelectorAll('tbody tr');
      expect(rows).toHaveLength(2);
      expect(rows[0].querySelector('th')?.textContent).toContain('ROBOT-01');
      expect(cells(rows[0])).toEqual(['87.3%', '—', '100.0%', '—', 'No ideal cycle time']);
      expect(cells(rows[1])).toEqual(['50.0%', '100.0%', '50.0%', '12.5%', '']);
      expect(element.querySelector('.date')?.textContent).toContain('2026-10-28');
    });

    it('asks for the start date of a night shift that began yesterday', () => {
      respondShifts([shift(2, 'NIGHT', '22:00:00', '06:00:00')]);
      respondMachines([robots[0]]);

      const req = http.expectOne((r) => r.url === oeeUrl(7));
      expect(req.request.params.get('date')).toBe('2026-10-27');
      req.flush(oee());
    });

    it('keeps the other rows when one machine fails', () => {
      respondShifts([shift(1, 'MORNING')]);
      respondMachines(robots);

      http.expectOne((r) => r.url === oeeUrl(7)).flush(null, { status: 500, statusText: 'Server Error' });
      http.expectOne((r) => r.url === oeeUrl(8)).flush(oee());
      fixture.detectChanges();

      const rows = element.querySelectorAll('tbody tr');
      expect(cells(rows[0])).toEqual(['—', '—', '—', '—', 'Could not load']);
      expect(cells(rows[1])[3]).toBe('100.0%');
    });

    it('says so when the line has no machines', () => {
      respondShifts([shift(1, 'MORNING')]);
      respondMachines([]);

      expect(element.querySelector('tbody')?.textContent).toContain('no machines');
    });
  });

  describe('refresh', () => {
    function loadOnce() {
      respondShifts([shift(1, 'MORNING')]);
      respondMachines([robots[0]]);
      http.expectOne((r) => r.url === oeeUrl(7)).flush(oee());
      fixture.detectChanges();
    }

    it('asks again every 30 seconds', () => {
      loadOnce();

      vi.advanceTimersByTime(30_000);
      http.expectOne((r) => r.url === MACHINES_URL).flush({ content: [robots[0]], page: {} });
      http.expectOne((r) => r.url === oeeUrl(7)).flush(oee());
    });

    it('keeps the last rows and warns when a refresh fails', () => {
      loadOnce();

      vi.advanceTimersByTime(30_000);
      http.expectOne((r) => r.url === MACHINES_URL).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not refresh');
      expect(element.querySelectorAll('tbody tr')).toHaveLength(1);
    });

    it('warns that the machines could not be loaded when the first load fails', () => {
      respondShifts([shift(1, 'MORNING')]);
      http.expectOne((r) => r.url === MACHINES_URL).flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load the machines');
      expect(element.textContent).not.toContain('no machines');
    });
  });
});
