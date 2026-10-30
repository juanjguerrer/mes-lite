import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Shift } from '../../core/models/shift';
import { ShiftSelector } from './shift-selector';

const URL = '/api/lines/3/shifts';

const shift = (id: number, code: string) => ({
  id,
  lineId: 3,
  code,
  name: `Shift ${code}`,
  startTime: '06:00:00',
  endTime: '14:00:00',
  crossesMidnight: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

describe('ShiftSelector', () => {
  let fixture: ComponentFixture<ShiftSelector>;
  let http: HttpTestingController;
  let element: HTMLElement;
  let emitted: (Shift | undefined)[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShiftSelector],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ShiftSelector);
    fixture.componentRef.setInput('lineId', 3);
    emitted = [];
    fixture.componentInstance.shiftChange.subscribe((s) => emitted.push(s));
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  function respond(shifts: unknown[]) {
    http.expectOne(URL).flush(shifts);
    fixture.detectChanges();
  }

  it('lists the shifts of the line with their hours', () => {
    respond([shift(1, 'MORNING'), shift(2, 'NIGHT')]);

    const options = [...element.querySelectorAll('option')].map((o) => o.textContent?.trim());
    expect(options).toHaveLength(2);
    expect(options[0]).toContain('MORNING');
    expect(options[0]).toContain('06:00–14:00');
  });

  it('selects and emits the first shift by default', () => {
    respond([shift(1, 'MORNING'), shift(2, 'NIGHT')]);

    expect((element.querySelector('select') as HTMLSelectElement).value).toBe('MORNING');
    expect(emitted.at(-1)?.code).toBe('MORNING');
  });

  it('emits the picked shift', () => {
    respond([shift(1, 'MORNING'), shift(2, 'NIGHT')]);
    const select = element.querySelector('select') as HTMLSelectElement;

    select.value = 'NIGHT';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(select.value).toBe('NIGHT');
    expect(emitted.at(-1)?.code).toBe('NIGHT');
  });

  it('says so and emits nothing when the line has no shifts', () => {
    respond([]);

    expect(element.querySelector('select')).toBeNull();
    expect(element.textContent).toContain('no shifts');
    expect(emitted.at(-1)).toBeUndefined();
  });

  it('warns when the shifts cannot be loaded', () => {
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load the shifts');
  });
});
