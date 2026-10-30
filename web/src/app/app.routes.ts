import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'lines/:lineId',
    loadComponent: () =>
      import('./features/line-dashboard/line-dashboard').then((m) => m.LineDashboard),
  },
];
