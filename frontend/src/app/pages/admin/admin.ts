import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

type Filtro = 'TODOS' | 'NOVO' | 'EM_ATENDIMENTO' | 'CONCLUIDO';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [RouterLink],
  styleUrl: './admin.scss',
  templateUrl: './admin.html',
})
export class Admin {
  readonly filtro = signal<Filtro>('TODOS');
  readonly filtros: { valor: Filtro; rotulo: string }[] = [
    { valor: 'TODOS', rotulo: 'Todos' },
    { valor: 'NOVO', rotulo: 'Novos' },
    { valor: 'EM_ATENDIMENTO', rotulo: 'Em atendimento' },
    { valor: 'CONCLUIDO', rotulo: 'Concluídos' },
  ];
  // Não requisitar dados pessoais até existir autenticação e autorização no backend.
  readonly autenticado = signal(false);
}
