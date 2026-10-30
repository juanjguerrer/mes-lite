import { Component, input, numberAttribute, signal } from '@angular/core';
import { Shift } from '../../core/models/shift';
import { MachineStates } from '../machine-states/machine-states';
import { OeeTable } from '../oee-table/oee-table';
import { ShiftSelector } from '../shift-selector/shift-selector';
import { StateDurationsChart } from '../state-durations-chart/state-durations-chart';

@Component({
  imports: [MachineStates, ShiftSelector, OeeTable, StateDurationsChart],
  selector: 'app-line-dashboard',
  styleUrl: './line-dashboard.scss',
  templateUrl: './line-dashboard.html',
})
export class LineDashboard {
  /** Bound to the `:lineId` route parameter. */
  readonly lineId = input.required({ transform: numberAttribute });

  /** The shift picked in the selector; the OEE table and the chart show this one. */
  protected readonly shift = signal<Shift | undefined>(undefined);
}
