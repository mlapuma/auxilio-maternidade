import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, switchMap, catchError, of, map } from 'rxjs';
import type { RespostasTriagem } from '../questionario/questionario';

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
  nome = '';
  email = '';
  telefone = '';
  consentimento = false;

  enviar(form: NgForm): void {
    if (form.invalid || !this.consentimento || this.enviando()) return;
    this.erro.set('');
    this.aviso.set('');
    this.enviando.set(true);
    this.http.post<LeadResponse>('/api/leads', {
      nome: this.nome.trim(),
      email: this.email.trim(),
      telefone: this.telefone.replace(/\D/g, ''),
      origem: 'SITE',
      avisoPrivacidadeVersao: 'v1',
      consentimentoContato: this.consentimento,
    }).pipe(
      switchMap(lead => {
        if (!this.respostasTriagem) return of({ lead, triagemSalva: false, naoAplicavel: true });
        return this.http.post('/api/triagens', {
          leadId: lead.id,
          respostas: this.respostasTriagem,
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
