import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

type Lead = { id: string; protocolo: string };
type Resultado = { classificacao: string; mensagem: string; encaminharAtendimento: boolean };

@Component({
 selector: 'app-root',
 standalone: true,
 imports: [FormsModule],
 template: `
 <main><h1>Salário-maternidade</h1>
 <p>Responda às perguntas para solicitar uma avaliação inicial pelo escritório. O resultado não garante direito ao benefício.</p>
 <form (ngSubmit)="enviar()" #form="ngForm">
 <label>Nome completo <input name="nome" [(ngModel)]="nome" required maxlength="160"></label>
 <label>Telefone <input name="telefone" [(ngModel)]="telefone" required minlength="10" maxlength="20" placeholder="11999999999"></label>
 <label>E-mail (opcional) <input name="email" type="email" [(ngModel)]="email"></label>
 <label>Houve nascimento ou adoção?
 <select name="evento" [(ngModel)]="evento" required><option [ngValue]="null">Selecione</option><option [ngValue]="true">Sim</option><option [ngValue]="false">Não</option></select></label>
 <label>Você possui ou já possuiu vínculo de trabalho ou contribuição previdenciária?
 <select name="vinculo" [(ngModel)]="vinculo" required><option [ngValue]="null">Selecione</option><option [ngValue]="true">Sim</option><option [ngValue]="false">Não / Não sei</option></select></label>
 <label><input type="checkbox" name="contato" [(ngModel)]="contato"> Aceito receber contato do escritório sobre esta solicitação (opcional).</label>
 <p>Ao enviar, os dados serão utilizados para registrar sua solicitação e permitir avaliação pelo escritório. Consulte o aviso de privacidade antes da publicação.</p>
 <button type="submit" [disabled]="!form.valid || evento === null || vinculo === null || enviando">Enviar solicitação</button>
 </form>
 @if (erro) { <p class="erro" role="alert">{{erro}}</p> }
 @if (resultado) { <section class="resultado" aria-live="polite"><h2>Solicitação registrada</h2><p>Protocolo: {{protocolo}}</p><p>{{resultado.mensagem}}</p></section> }
 </main>`
})
class AppComponent {
 private readonly http = inject(HttpClient);
 nome=''; telefone=''; email=''; evento:boolean|null=null; vinculo:boolean|null=null; contato=false;
 enviando=false; erro=''; resultado:Resultado|null=null; protocolo='';
 async enviar() {
   this.enviando=true; this.erro=''; this.resultado=null;
   try {
     const lead = await firstValueFrom(this.http.post<Lead>('/api/leads', {
       nome:this.nome, telefone:this.telefone, email:this.email || null, origem:'landing',
       avisoPrivacidadeVersao:'v1', consentimentoContato:this.contato
     }));
     this.protocolo=lead.protocolo;
     this.resultado=await firstValueFrom(this.http.post<Resultado>('/api/triagens', {
       leadId:lead.id, respostas:{houveNascimentoOuAdocao:this.evento, possuiVinculoOuContribuicao:this.vinculo, desejaAtendimento:this.contato}
     }));
   } catch { this.erro='Não foi possível concluir a solicitação. Se recebeu um protocolo, informe-o ao escritório para evitar duplicidade.'; }
   finally { this.enviando=false; }
 }
}
bootstrapApplication(AppComponent, {providers:[provideHttpClient()]}).catch(console.error);
