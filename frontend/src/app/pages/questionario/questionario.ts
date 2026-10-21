import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

export interface RespostasTriagem {
  houveNascimentoOuAdocao: boolean;
  possuiVinculoOuContribuicao: boolean;
  desejaAtendimento: boolean;
}

@Component({
  selector: 'app-questionario',
  standalone: true,
  imports: [FormsModule, RouterLink],
  styleUrl: './questionario.scss',
  templateUrl: './questionario.html',
})
export class Questionario {
  private readonly router = inject(Router);
  readonly etapa = signal(0);
  readonly perguntas = [
    { titulo: 'Já ocorreu nascimento ou adoção?', opcoes: ['Sim', 'Não'] },
    { titulo: 'Você possui vínculo de trabalho ou já contribuiu para o INSS?', opcoes: ['Sim', 'Não', 'Não tenho certeza'] },
    { titulo: 'Deseja que o escritório entre em contato?', opcoes: ['Sim', 'Não'] },
  ];
  respostas: string[] = ['', '', ''];
  get concluido(): boolean { return this.etapa() >= this.perguntas.length; }
  avancar(): void { if (this.respostas[this.etapa()]) this.etapa.update(valor => valor + 1); }
  voltar(): void { this.etapa.update(valor => Math.max(0, valor - 1)); }
  irParaContato(): void {
    if (!this.concluido || this.respostas.some(r => !r)) return;
    // Não classificar respostas incertas como negativas: encaminhar para avaliação humana.
    const respostasTriagem: RespostasTriagem | null = this.respostas[1] === 'Não tenho certeza' ? null : {
      houveNascimentoOuAdocao: this.respostas[0] === 'Sim',
      possuiVinculoOuContribuicao: this.respostas[1] === 'Sim',
      desejaAtendimento: this.respostas[2] === 'Sim',
    };
    this.router.navigate(['/contato'], { state: { respostasTriagem } });
  }
}
