import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Oee } from './oee';

describe('Oee', () => {
  let service: Oee;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Oee);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets the OEE of a machine by shift and plant date', () => {
    service.byShift(7, 'A', '2026-10-22').subscribe();

    const req = http.expectOne((r) => r.url === '/api/machines/7/oee');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('shiftCode')).toBe('A');
    expect(req.request.params.get('date')).toBe('2026-10-22');
    expect(req.request.params.has('from')).toBe(false);
    req.flush({});
  });

  it('gets the OEE of a machine by range with ISO instants', () => {
    service.byRange(7, new Date('2026-10-22T08:00:00Z'), new Date('2026-10-22T12:00:00Z')).subscribe();

    const req = http.expectOne((r) => r.url === '/api/machines/7/oee');
    expect(req.request.params.get('from')).toBe('2026-10-22T08:00:00.000Z');
    expect(req.request.params.get('to')).toBe('2026-10-22T12:00:00.000Z');
    expect(req.request.params.has('shiftCode')).toBe(false);
    req.flush({});
  });
});
