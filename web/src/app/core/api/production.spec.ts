import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Production } from './production';

describe('Production', () => {
  let service: Production;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Production);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets the hourly production of a machine with ISO instants', () => {
    let result: unknown;
    service
      .hourly(7, new Date('2026-10-22T08:00:00Z'), new Date('2026-10-22T12:00:00Z'))
      .subscribe((value) => (result = value));

    const req = http.expectOne((r) => r.url === '/api/machines/7/production-counts/hourly');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-10-22T08:00:00.000Z');
    expect(req.request.params.get('to')).toBe('2026-10-22T12:00:00.000Z');
    const body = { machineId: 7, buckets: [] };
    req.flush(body);

    expect(result).toEqual(body);
  });
});
