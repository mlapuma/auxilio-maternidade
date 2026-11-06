import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Contato } from './contato';
import { WhatsAppNavigation } from './whatsapp-navigation';
import { NgForm } from '@angular/forms';

describe('Contato: continuação do questionário', () => {
  let http: HttpTestingController;
  let component: Contato;
  const abrir = vi.fn();
  const resetForm = vi.fn();
  const form = { invalid: false, resetForm } as unknown as NgForm;
  const lead = { id: 'lead-teste', protocolo: 'SM-TESTE' };
  const whatsappUrl = 'https://wa.me/551155555555?text=Codigo+SM-TESTE';

  beforeEach(() => {
    history.replaceState({ respostasTriagem: {
      houveNascimentoOuAdocao: false, possuiVinculoOuContribuicao: false,
      desejaAtendimento: true, respostasDetalhadas: { teste: 'Resposta já informada' },
    } }, '');
    abrir.mockReset();
    resetForm.mockReset();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: WhatsAppNavigation, useValue: { abrir } }],
    });
    http = TestBed.inject(HttpTestingController);
    component = TestBed.runInInjectionContext(() => new Contato());
    component.nome = 'Contato Teste';
    component.telefone = '(11) 90000-0000';
    component.consentimento = true;
  });

  afterEach(() => {
    http.verify();
    history.replaceState(null, '');
  });

  function salvarTriagem() {
    component.enviar(form);
    http.expectOne('/api/leads').flush(lead);
    const triagem = http.expectOne('/api/triagens');
    expect(triagem.request.body.respostasDetalhadas.teste).toBe('Resposta já informada');
    triagem.flush({});
  }

  it('abre somente depois de salvar contato, respostas e atendimento', () => {
    salvarTriagem();
    expect(abrir).not.toHaveBeenCalled();
    expect(component.enviando()).toBe(true);
    http.expectOne('/api/atendimentos').flush({ whatsappUrl });
    expect(abrir).toHaveBeenCalledExactlyOnceWith(whatsappUrl);
    expect(component.whatsappUrl()).toBe(whatsappUrl);
    expect(component.protocolo()).toBe(lead.protocolo);
    expect(component.enviando()).toBe(false);
    expect(resetForm).toHaveBeenCalledOnce();
  });

  it('preserva o protocolo e avisa quando o número não está configurado', () => {
    salvarTriagem();
    http.expectOne('/api/atendimentos').flush({ whatsappUrl: null });
    expect(abrir).not.toHaveBeenCalled();
    expect(component.protocolo()).toBe(lead.protocolo);
    expect(component.aviso()).toContain('temporariamente indisponível');
  });

  it('não perde o cadastro quando a criação do atendimento falha', () => {
    salvarTriagem();
    http.expectOne('/api/atendimentos').flush({}, { status: 500, statusText: 'Erro' });
    expect(abrir).not.toHaveBeenCalled();
    expect(component.protocolo()).toBe(lead.protocolo);
    expect(component.enviando()).toBe(false);
  });

  it('não redireciona quando as respostas não foram salvas', () => {
    component.enviar(form);
    http.expectOne('/api/leads').flush(lead);
    http.expectOne('/api/triagens').flush({}, { status: 500, statusText: 'Erro' });
    http.expectNone('/api/atendimentos');
    expect(abrir).not.toHaveBeenCalled();
    expect(component.aviso()).toContain('não foram salvas');
  });

  it('não inventa respostas para um contato que entrou sem questionário', () => {
    history.replaceState(null, '');
    component = TestBed.runInInjectionContext(() => new Contato());
    component.consentimento = true;
    component.enviar(form);
    http.expectOne('/api/leads').flush(lead);
    http.expectNone('/api/triagens');
    http.expectNone('/api/atendimentos');
    expect(abrir).not.toHaveBeenCalled();
  });
});
