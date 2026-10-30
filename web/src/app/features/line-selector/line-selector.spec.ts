import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LineSelector } from './line-selector';

describe('LineSelector', () => {
  let component: LineSelector;
  let fixture: ComponentFixture<LineSelector>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LineSelector],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(LineSelector);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
