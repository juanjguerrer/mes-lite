import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Shifts } from './shifts';

describe('Shifts', () => {
  let service: Shifts;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Shifts);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the shifts of a line', () => {
    let result: unknown;
    service.listByLine(3).subscribe((value) => (result = value));

    const req = http.expectOne('/api/lines/3/shifts');
    expect(req.request.method).toBe('GET');
    req.flush([]);

    expect(result).toEqual([]);
  });
});
