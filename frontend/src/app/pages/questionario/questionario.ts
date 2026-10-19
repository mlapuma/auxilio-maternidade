import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-questionario',
  standalone: true,
  imports: [FormsModule, RouterLink],
  styleUrl: './questionario.scss',
  templateUrl: './questionario.html',
})
export class Questionario {
  readonly etapa = signal(0);
  readonly perguntas = [
    { titulo: 'Qual é a sua situação atual?', opcoes: ['Empregada com carteira assinada', 'MEI ou autônoma', 'Desempregada', 'Outra situação'] },
    { titulo: 'Qual situação motivou sua busca?', opcoes: ['Nascimento de filho(a)', 'Adoção ou guarda para adoção', 'Gestação em andamento', 'Outra situação'] },
    { titulo: 'Você já contribuiu para o INSS?', opcoes: ['Sim', 'Não', 'Não tenho certeza'] },
  ];
  respostas: string[] = ['', '', ''];
  get concluido(): boolean { return this.etapa() >= this.perguntas.length; }
  avancar(): void { if (this.respostas[this.etapa()]) this.etapa.update(valor => valor + 1); }
  voltar(): void { this.etapa.update(valor => Math.max(0, valor - 1)); }
}
