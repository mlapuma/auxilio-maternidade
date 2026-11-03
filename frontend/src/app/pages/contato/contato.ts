import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, switchMap, catchError, of, map } from 'rxjs';
import type { RespostasTriagem } from '../questionario/questionario';

interface AtendimentoResponse { id: string; codigoPublico: string; status: string; whatsappUrl: string | null; }
interface LeadResponse { id: string; protocolo: string; status: string; criadoEm: string; }

@Component({
  selector: 'app-contato',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './contato.html',
  styleUrl: './contato.scss',
})
export class Contato {
  private readonly http = inject(HttpClient);
  private readonly respostasTriagem: RespostasTriagem | null = history.state?.respostasTriagem ?? null;
  readonly enviando = signal(false);
  readonly protocolo = signal('');
  readonly erro = signal('');
  readonly aviso = signal('');
  readonly whatsappUrl = signal<string | null>(null);
  nome = '';
  email = '';
  telefone = '';
  consentimento = false;

  formatarTelefone(valor: string): void {
    const numeros = valor.replace(/\D/g, '').slice(0, 11);
    const ddd = numeros.slice(0, 2);
    const prefixo = numeros.slice(2, 7);
    const sufixo = numeros.slice(7, 11);
    this.telefone = numeros.length > 7
      ? `(${ddd}) ${prefixo}-${sufixo}`
      : numeros.length > 2
        ? `(${ddd}) ${numeros.slice(2)}`
        : numeros.length > 0 ? `(${ddd}` : '';
  }

  enviar(form: NgForm): void {
    if (form.invalid || !this.consentimento || this.enviando()) return;
    this.erro.set('');
    this.aviso.set('');
    this.whatsappUrl.set(null);
    this.enviando.set(true);
    this.http.post<LeadResponse>('/api/leads', {
      nome: this.nome.trim(),
      email: this.email.trim(),
      telefone: this.telefone.replace(/\D/g, ''),
      origem: 'SITE',
      utmSource: new URLSearchParams(window.location.search).get('utm_source')?.slice(0, 120) ?? null,
      utmMedium: new URLSearchParams(window.location.search).get('utm_medium')?.slice(0, 120) ?? null,
      utmCampaign: new URLSearchParams(window.location.search).get('utm_campaign')?.slice(0, 160) ?? null,
      avisoPrivacidadeVersao: 'v1',
      consentimentoContato: this.consentimento,
    }).pipe(
      switchMap(lead => {
        if (!this.respostasTriagem) return of({ lead, triagemSalva: false, naoAplicavel: true });
        return this.http.post('/api/triagens', {
          leadId: lead.id,
          respostas: {
            houveNascimentoOuAdocao: this.respostasTriagem.houveNascimentoOuAdocao,
            possuiVinculoOuContribuicao: this.respostasTriagem.possuiVinculoOuContribuicao,
            desejaAtendimento: this.respostasTriagem.desejaAtendimento,
          },
          respostasDetalhadas: this.respostasTriagem.respostasDetalhadas,
        }).pipe(
          map(() => ({ lead, triagemSalva: true, naoAplicavel: false })),
          catchError(() => of({ lead, triagemSalva: false, naoAplicavel: false })),
        );
      }),
      finalize(() => this.enviando.set(false)),
    ).subscribe({
      next: ({ lead, triagemSalva, naoAplicavel }) => {
        this.protocolo.set(lead.protocolo);
        if (!triagemSalva && !naoAplicavel) this.aviso.set('Contato registrado, mas as respostas do questionário não foram salvas. Informe seu protocolo à equipe.');
        if (naoAplicavel) this.aviso.set('A equipe fará a avaliação individual das informações.');
        if (triagemSalva) {
          this.http.post<AtendimentoResponse>('/api/atendimentos', { leadId: lead.id }).subscribe({
            next: atendimento => this.whatsappUrl.set(atendimento.whatsappUrl),
            error: () => this.aviso.set('Contato registrado. A continuação pelo WhatsApp está temporariamente indisponível.'),
          });
        }
        form.resetForm();
      },
      error: (error: HttpErrorResponse) => {
        this.erro.set(error.status === 0
          ? 'Não foi possível conectar ao servidor. Tente novamente.'
          : error.status === 400
            ? 'Verifique os dados informados e tente novamente.'
            : 'Não foi possível registrar o contato. Tente novamente mais tarde.');
      },
    });
  }
}
