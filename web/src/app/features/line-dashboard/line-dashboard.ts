import { Component, input, numberAttribute } from '@angular/core';
import { MachineStates } from '../machine-states/machine-states';
import { OeeTable } from '../oee-table/oee-table';

@Component({
  imports: [
    MachineStates,
    OeeTable
  ],
  selector: 'app-line-dashboard',
  styleUrl: './line-dashboard.scss',
  templateUrl: './line-dashboard.html',
})
export class LineDashboard {
  /** Bound to the `:lineId` route parameter. */
  readonly lineId = input.required({ transform: numberAttribute });
}
