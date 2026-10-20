import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

interface LeadResumo {
  id: string;
  protocolo: string;
  nome: string;
  telefone: string;
  email: string | null;
  status: string;
  criadoEm: string;
}
interface PaginaLeads {
  content: LeadResumo[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  styleUrl: './admin.scss',
  templateUrl: './admin.html',
})
export class Admin {
  private readonly http = inject(HttpClient);
  readonly autenticado = signal(false);
  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly pagina = signal<PaginaLeads | null>(null);
  readonly filtro = signal('TODOS');
  readonly filtros = [
    { valor: 'TODOS', rotulo: 'Todos' },
    { valor: 'NOVO', rotulo: 'Novos' },
    { valor: 'EM_ATENDIMENTO', rotulo: 'Em atendimento' },
    { valor: 'CONCLUIDO', rotulo: 'Concluídos' },
  ];
  usuario = '';
  senha = '';
  private autorizacao = '';

  entrar(): void {
    if (!this.usuario.trim() || !this.senha || this.carregando()) return;
    // Credenciais apenas em memória; nunca armazenar em localStorage ou URL.
    this.autorizacao = 'Basic ' + btoa(
      Array.from(new TextEncoder().encode(this.usuario + ':' + this.senha), b => String.fromCharCode(b)).join('')
    );
    this.senha = '';
    this.carregar(0);
  }

  carregar(numero: number): void {
    if (!this.autorizacao || this.carregando() || numero < 0) return;
    this.carregando.set(true);
    this.erro.set('');
    this.http.get<PaginaLeads>('/api/admin/leads', {
      headers: new HttpHeaders({ Authorization: this.autorizacao }),
      params: { page: numero, size: 20, sort: 'criadoEm,desc' },
    }).pipe(finalize(() => this.carregando.set(false))).subscribe({
      next: resultado => {
        this.pagina.set(resultado);
        this.autenticado.set(true);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.sair();
          this.erro.set('Credenciais inválidas ou acesso não autorizado.');
        } else {
          this.erro.set('Não foi possível consultar os atendimentos. Verifique a conexão com o servidor.');
        }
      },
    });
  }

  sair(): void {
    this.autorizacao = '';
    this.usuario = '';
    this.senha = '';
    this.autenticado.set(false);
    this.pagina.set(null);
    this.filtro.set('TODOS');
  }

  alterarFiltro(valor: string): void { this.filtro.set(valor); }

  get registrosFiltrados(): LeadResumo[] {
    const dados = this.pagina()?.content ?? [];
    return this.filtro() === 'TODOS' ? dados : dados.filter(lead => lead.status === this.filtro());
  }
}
