import { TestBed } from '@angular/core/testing';
import { Lines } from './lines';

describe('Lines', () => {
  let service: Lines;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Lines);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
