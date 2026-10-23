package com.portalcursos.ng02.dto;

/** Resultado da busca de aluno no formulário de cobrança: só o necessário para escolher, com o CPF mascarado. */
public record StudentSearchHit(Long id, String fullName, String cpfMasked, String registrationNumber) {
}
