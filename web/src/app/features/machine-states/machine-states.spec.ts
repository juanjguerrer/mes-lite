import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MachineStates } from './machine-states';

const URL = '/api/lines/3/machines/states';

const running = {
  machineId: 1,
  machineCode: 'ROBOT-01',
  state: 'RUNNING',
  since: '2026-10-28T14:05:00Z',
};
const noEvents = { machineId: 2, machineCode: 'TESTER-01', state: null, since: null };

describe('MachineStates', () => {
  let fixture: ComponentFixture<MachineStates>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [MachineStates],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MachineStates);
    fixture.componentRef.setInput('lineId', 3);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    // First poll: timer(0, ...) fires on the next tick.
    vi.advanceTimersByTime(0);
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function respond(body: unknown[]) {
    http.expectOne(URL).flush(body);
    fixture.detectChanges();
  }

  function fail() {
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
  }

  it('shows a card per machine, ordered by id', () => {
    respond([noEvents, running]);

    const codes = [...element.querySelectorAll('.code')].map((e) => e.textContent);
    expect(codes).toEqual(['ROBOT-01', 'TESTER-01']);
    expect(element.querySelector('.card .state')?.textContent).toContain('Running');
  });

  it('shows "No data" for a machine without events', () => {
    respond([noEvents]);

    const card = element.querySelector('.card');
    expect(card?.classList).toContain('no-data');
    expect(card?.textContent).toContain('No data');
  });

  it('does not say "No machines" before the first response', () => {
    expect(element.textContent).not.toContain('No machines');
    respond([]);
    expect(element.textContent).toContain('No machines');
  });

  it('polls again every 5 seconds', () => {
    respond([running]);

    vi.advanceTimersByTime(5000);
    http.expectOne(URL).flush([running]);
  });

  it('keeps the last data and warns when a refresh fails', () => {
    respond([running]);

    vi.advanceTimersByTime(5000);
    fail();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not refresh');
    expect(element.querySelectorAll('.card')).toHaveLength(1);
  });

  it('warns that the machines could not be loaded when the first request fails', () => {
    fail();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load');
    expect(element.textContent).not.toContain('No machines');
  });

  it('clears the warning when a later refresh succeeds', () => {
    respond([running]);
    vi.advanceTimersByTime(5000);
    fail();

    vi.advanceTimersByTime(5000);
    respond([running]);

    expect(element.querySelector('[role="alert"]')).toBeNull();
  });
});
