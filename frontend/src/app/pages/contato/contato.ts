import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-contato',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './contato.html',
  styleUrl: './contato.scss',
})
export class Contato {
  private readonly http = inject(HttpClient);
  readonly enviando = signal(false);
  readonly enviado = signal(false);
  readonly erro = signal('');
  nome = '';
  email = '';
  telefone = '';
  consentimento = false;

  enviar(form: NgForm): void {
    if (form.invalid || !this.consentimento || this.enviando()) return;
    this.erro.set('');
    this.enviando.set(true);
    this.http.post('/api/leads', {
      nome: this.nome.trim(),
      email: this.email.trim(),
      telefone: this.telefone.replace(/\D/g, ''),
    }).pipe(finalize(() => this.enviando.set(false))).subscribe({
      next: () => { this.enviado.set(true); form.resetForm(); },
      error: (error: HttpErrorResponse) => {
        this.erro.set(error.status === 0
          ? 'Não foi possível conectar ao servidor. Tente novamente.'
          : 'Não foi possível registrar o contato. Tente novamente mais tarde.');
      },
    });
  }
}
