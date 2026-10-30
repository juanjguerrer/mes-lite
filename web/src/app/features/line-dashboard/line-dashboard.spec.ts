import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LineDashboard } from './line-dashboard';

describe('LineDashboard', () => {
  let component: LineDashboard;
  let fixture: ComponentFixture<LineDashboard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LineDashboard],
    }).compileComponents();

    fixture = TestBed.createComponent(LineDashboard);
    fixture.componentRef.setInput('lineId', '3');
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
