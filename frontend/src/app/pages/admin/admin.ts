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
interface TriagemDetalhe {
  id: string;
  classificacao: string;
  houveNascimentoOuAdocao: boolean;
  possuiVinculoOuContribuicao: boolean;
  desejaAtendimento: boolean;
  criadoEm: string;
}
interface HistoricoResumo {
  id: string;
  statusAnterior: string;
  statusNovo: string;
  alteradoEm: string;
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
  readonly salvandoId = signal<string | null>(null);
  readonly leadDetalhado = signal<LeadResumo | null>(null);
  readonly triagens = signal<TriagemDetalhe[]>([]);
  readonly historico = signal<HistoricoResumo[]>([]);
  readonly carregandoHistorico = signal(false);
  readonly carregandoTriagens = signal(false);
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

  atualizarStatus(lead: LeadResumo, novoStatus: string): void {
    if (!this.autorizacao || this.carregando() || this.salvandoId() || novoStatus === lead.status) return;
    if (!['NOVO', 'EM_ATENDIMENTO', 'CONCLUIDO'].includes(novoStatus)) return;
    this.erro.set('');
    this.salvandoId.set(lead.id);
    this.http.patch<LeadResumo>(
      '/api/admin/leads/' + encodeURIComponent(lead.id) + '/status',
      { status: novoStatus },
      { headers: new HttpHeaders({ Authorization: this.autorizacao }) },
    ).pipe(finalize(() => this.salvandoId.set(null))).subscribe({
      next: atualizado => {
        this.pagina.update(pagina => pagina ? {
          ...pagina,
          content: pagina.content.map(item => item.id === atualizado.id ? atualizado : item),
        } : null);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.sair();
          this.erro.set('Sua sessão não está autorizada. Entre novamente.');
        } else {
          this.erro.set('Não foi possível atualizar o atendimento. Tente novamente.');
        }
      },
    });
  }

  consultarTriagens(lead: LeadResumo): void {
    if (!this.autorizacao || this.carregandoTriagens()) return;
    this.leadDetalhado.set(lead);
    this.triagens.set([]);
    this.erro.set('');
    this.carregandoTriagens.set(true);
    this.http.get<TriagemDetalhe[]>(
      '/api/admin/leads/' + encodeURIComponent(lead.id) + '/triagens',
      { headers: new HttpHeaders({ Authorization: this.autorizacao }) },
    ).pipe(finalize(() => this.carregandoTriagens.set(false))).subscribe({
      next: dados => this.triagens.set(dados),
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.sair();
          this.erro.set('Acesso não autorizado. Entre novamente.');
        } else {
          this.erro.set('Não foi possível consultar o questionário.');
        }
      },
    });
  }

  consultarHistorico(lead: LeadResumo): void {
    if (!this.autorizacao || this.carregandoHistorico()) return;
    this.leadDetalhado.set(lead);
    this.historico.set([]);
    this.erro.set('');
    this.carregandoHistorico.set(true);
    this.http.get<HistoricoResumo[]>(
      '/api/admin/leads/' + encodeURIComponent(lead.id) + '/historico',
      { headers: new HttpHeaders({ Authorization: this.autorizacao }) },
    ).pipe(finalize(() => this.carregandoHistorico.set(false))).subscribe({
      next: dados => this.historico.set(dados),
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.sair();
          this.erro.set('Acesso não autorizado. Entre novamente.');
        } else {
          this.erro.set('Não foi possível consultar o histórico.');
        }
      },
    });
  }

  fecharDetalhes(): void {
    this.leadDetalhado.set(null);
    this.triagens.set([]);
    this.historico.set([]);
  }

  sair(): void {
    this.autorizacao = '';
    this.usuario = '';
    this.senha = '';
    this.autenticado.set(false);
    this.pagina.set(null);
    this.fecharDetalhes();
    this.filtro.set('TODOS');
  }

  alterarFiltro(valor: string): void { this.filtro.set(valor); }

  get registrosFiltrados(): LeadResumo[] {
    const dados = this.pagina()?.content ?? [];
    return this.filtro() === 'TODOS' ? dados : dados.filter(lead => lead.status === this.filtro());
  }
}
