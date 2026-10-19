import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

interface LeadResponse { protocolo: string; status: string; }

@Component({
  selector: 'app-contato',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styleUrl: './contato.scss',
  templateUrl: './contato.html'
})
export class Contato {
  private readonly http = inject(HttpClient);
  nome = '';
  telefone = '';
  email = '';
  consentimentoContato = false;
  enviando = false;
  erro = '';
  protocolo = '';

  enviar(): void {
    if (this.enviando || !this.nome.trim() || !this.telefone.trim() || !this.consentimentoContato) return;
    this.enviando = true;
    this.erro = '';
    this.http.post<LeadResponse>('http://localhost:8080/api/leads', {
      nome: this.nome.trim(),
      telefone: this.telefone.trim(),
      email: this.email.trim() || null,
      origem: 'landing-page',
      avisoPrivacidadeVersao: 'v1',
      consentimentoContato: true
    }).subscribe({
      next: response => { this.protocolo = response.protocolo; this.enviando = false; },
      error: (error: HttpErrorResponse) => {
        this.erro = error.status === 0
          ? 'Não foi possível conectar ao servidor. Verifique se o backend está ligado.'
          : 'Não foi possível registrar o contato. Confira os dados e tente novamente.';
        this.enviando = false;
      }
    });
  }
}
