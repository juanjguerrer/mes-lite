import { TestBed } from '@angular/core/testing';
import { Oee } from './oee';

describe('Oee', () => {
  let service: Oee;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Oee);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
