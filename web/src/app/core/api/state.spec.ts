import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { State } from './state';

describe('State', () => {
  let service: State;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(State);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets the current state of every machine in a line', () => {
    service.currentByLine(3).subscribe();

    const req = http.expectOne('/api/lines/3/machines/states');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('gets the state durations of a machine with ISO instants', () => {
    const from = new Date('2026-10-22T08:00:00Z');
    const to = new Date('2026-10-22T12:00:00Z');
    service.durations(7, from, to).subscribe();

    const req = http.expectOne((r) => r.url === '/api/machines/7/state-durations');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-10-22T08:00:00.000Z');
    expect(req.request.params.get('to')).toBe('2026-10-22T12:00:00.000Z');
    req.flush({});
  });
});
