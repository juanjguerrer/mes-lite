import { TestBed } from '@angular/core/testing';
import { Production } from './production';

describe('Production', () => {
  let service: Production;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Production);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
