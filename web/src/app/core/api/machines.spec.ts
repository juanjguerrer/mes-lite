import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Machines } from './machines';

describe('Machines', () => {
  let service: Machines;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Machines);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the machines of a line asking for up to 100 per page', () => {
    service.listByLine(3).subscribe();

    const req = http.expectOne((r) => r.url === '/api/lines/3/machines');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('size')).toBe('100');
    req.flush({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } });
  });
});
