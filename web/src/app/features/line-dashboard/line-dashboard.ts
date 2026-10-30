import { Component, input, numberAttribute } from '@angular/core';
import { MachineStates } from '../machine-states/machine-states';

@Component({
  imports: [
    MachineStates
  ],
  selector: 'app-line-dashboard',
  styleUrl: './line-dashboard.scss',
  templateUrl: './line-dashboard.html',
})
export class LineDashboard {
  /** Bound to the `:lineId` route parameter. */
  readonly lineId = input.required({ transform: numberAttribute });
}
