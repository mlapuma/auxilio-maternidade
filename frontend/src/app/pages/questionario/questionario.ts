import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

export interface RespostasTriagem {
  houveNascimentoOuAdocao: boolean;
  possuiVinculoOuContribuicao: boolean;
  desejaAtendimento: boolean;
}

interface Pergunta {
  id: string;
  titulo: string;
  opcoes: string[];
  dica?: string;
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
  readonly perguntasBase: Pergunta[] = [
    { id: 'situacao', titulo: 'Qual é a sua situação neste momento?', opcoes: ['Estou grávida', 'Meu bebê já nasceu', 'Adoção ou guarda para adoção', 'Outra situação / preciso de orientação'] },
    { id: 'trabalho', titulo: 'Você está trabalhando atualmente?', opcoes: ['Sim, com carteira assinada', 'Sim, como MEI ou autônoma', 'Sim, como doméstica ou trabalhadora rural', 'Sim, como servidora pública', 'Não estou trabalhando', 'Outra situação / não sei informar'] },
    { id: 'inss', titulo: 'Você tem ou teve vínculo com o INSS?', opcoes: ['Tenho vínculo ou contribuo atualmente', 'Já trabalhei ou contribuí anteriormente', 'Nunca contribuí / não sei se tenho vínculo', 'Não tenho certeza'], dica: 'A qualidade de segurada pode ser mantida por um período mesmo após parar de contribuir. A análise depende do histórico individual.' },
    { id: 'contribuicao', titulo: 'Quando foi seu último vínculo ou contribuição?', opcoes: ['Atualmente', 'Há menos de 12 meses', 'Entre 12 e 24 meses', 'Há mais de 24 meses', 'Nunca contribuí', 'Não sei informar'], dica: 'Não existe exigência geral de dez contribuições para o salário-maternidade nos novos requerimentos. A qualidade de segurada continua relevante.' },
    { id: 'semanas', titulo: 'Quantas semanas de gestação você tem?', opcoes: ['Até 12 semanas', '13 a 20 semanas', '21 a 27 semanas', '28 a 32 semanas', '33 a 36 semanas', '37 semanas ou mais', 'Não sei informar'], dica: 'Em determinadas categorias, o afastamento pode começar até 28 dias antes do parto, com atestado médico. Isso não significa que toda gestante possa solicitar o benefício na 28ª semana.' },
    { id: 'parto', titulo: 'Quando ocorreu o nascimento?', opcoes: ['Há menos de 120 dias', 'Entre 120 dias e 1 ano', 'Há mais de 1 ano', 'Não sei informar'], dica: 'O prazo de 120 dias é, em regra, a duração do benefício, e não um prazo geral máximo para fazer o pedido.' },
    { id: 'adocao', titulo: 'A adoção ou guarda judicial para adoção já ocorreu?', opcoes: ['Sim', 'Ainda não', 'Não sei informar'] },
    { id: 'atendimento', titulo: 'Deseja receber uma análise individual do escritório?', opcoes: ['Sim, quero orientação', 'Não, por enquanto'] },
  ];

  respostas: Record<string, string> = {};
  get perguntas(): Pergunta[] {
    const situacao = this.respostas['situacao'];
    return this.perguntasBase.filter(p =>
      (p.id !== 'semanas' || situacao === 'Estou grávida') &&
      (p.id !== 'parto' || situacao === 'Meu bebê já nasceu') &&
      (p.id !== 'adocao' || situacao === 'Adoção ou guarda para adoção')
    );
  }
  get perguntaAtual(): Pergunta | undefined { return this.perguntas[this.etapa()]; }
  get concluido(): boolean { return this.etapa() >= this.perguntas.length; }
  avancar(): void {
    if (this.perguntaAtual && this.respostas[this.perguntaAtual.id]) this.etapa.update(v => v + 1);
  }
  voltar(): void { this.etapa.update(v => Math.max(0, v - 1)); }
  irParaContato(): void {
    if (!this.concluido || this.perguntas.some(p => !this.respostas[p.id])) return;
    const situacao = this.respostas['situacao'];
    const inss = this.respostas['inss'];
    const trabalho = this.respostas['trabalho'];
    const temVinculo = inss === 'Tenho vínculo ou contribuo atualmente' || inss === 'Já trabalhei ou contribuí anteriormente' || trabalho.startsWith('Sim,');
    const certezaVinculo = temVinculo;
    const houveEvento = situacao === 'Meu bebê já nasceu' || (situacao === 'Adoção ou guarda para adoção' && this.respostas['adocao'] === 'Sim');
    const respostasTriagem: RespostasTriagem | null = certezaVinculo ? {
      houveNascimentoOuAdocao: houveEvento,
      possuiVinculoOuContribuicao: true,
      desejaAtendimento: this.respostas['atendimento'] === 'Sim, quero orientação',
    } : null;
    // Não converter incerteza previdenciária em negativa automática.
    this.router.navigate(['/contato'], { state: { respostasTriagem } });
  }
}
