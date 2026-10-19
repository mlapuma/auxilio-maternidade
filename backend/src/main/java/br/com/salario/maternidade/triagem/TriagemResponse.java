package br.com.salario.maternidade.triagem;

public record TriagemResponse(String classificacao, String mensagem, boolean encaminharAtendimento) {}
