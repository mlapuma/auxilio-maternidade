import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/home/home').then(m => m.Home),
    title: 'Auxílio Maternidade'
  },
  {
    path: 'questionario',
    loadComponent: () =>
      import('./pages/questionario/questionario').then(m => m.Questionario),
    title: 'Pré-análise'
  },
  {
    path: 'resultado',
    loadComponent: () =>
      import('./pages/resultado/resultado').then(m => m.Resultado),
    title: 'Resultado da pré-análise'
  },
  {
    path: 'contato',
    loadComponent: () =>
      import('./pages/contato/contato').then(m => m.Contato),
    title: 'Contato'
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./pages/admin/admin').then(m => m.Admin),
    title: 'Painel administrativo'
  },
  {
    path: '**',
    redirectTo: ''
  }
];
