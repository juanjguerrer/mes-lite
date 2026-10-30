import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Lines } from './lines';

describe('Lines', () => {
  let service: Lines;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Lines);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists lines asking for up to 100 per page', () => {
    const page = { content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } };
    let result: unknown;
    service.list().subscribe((value) => (result = value));

    const req = http.expectOne((r) => r.url === '/api/lines');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page);

    expect(result).toEqual(page);
  });
});
