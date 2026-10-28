import { TestBed } from '@angular/core/testing';
import { Shifts } from './shifts';

describe('Shifts', () => {
  let service: Shifts;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Shifts);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
