import { TestBed } from '@angular/core/testing';
import { Machines } from './machines';

describe('Machines', () => {
  let service: Machines;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Machines);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
