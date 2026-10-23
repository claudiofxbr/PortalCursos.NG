package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.StudentSearchHit;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Busca de aluno por nome ou CPF para o formulário de nova cobrança. Devolve poucos campos e o CPF mascarado:
 * quem lança cobrança precisa reconhecer o aluno, não receber o cadastro.
 */
@Service
@RequiredArgsConstructor
public class FinanceStudentSearchService {

    static final int MIN_CHARS = 3;
    static final int MAX_RESULTS = 10;

    private final StudentRepository studentRepository;
    private final PostgradStudentRepository postgradStudentRepository;

    @Transactional(readOnly = true)
    public List<StudentSearchHit> search(String query, EAcademicLevel level) {
        String text = query == null ? "" : query.trim();
        // % e _ são curingas do LIKE; o que o usuário digita é sempre texto literal.
        String name = text.replace("\\", "").replace("%", "").replace("_", "").toLowerCase();
        String digits = text.replaceAll("\\D", "");
        // Parece CPF (só dígitos e pontuação): procura por CPF; senão, por nome.
        boolean byCpf = !digits.isEmpty() && text.matches("[\\d.\\-\\s]+");
        if ((byCpf ? digits.length() : name.length()) < MIN_CHARS) {
            throw new BusinessException("Digite ao menos " + MIN_CHARS + " caracteres do nome ou do CPF");
        }
        PageRequest page = PageRequest.of(0, MAX_RESULTS);
        List<? extends Student> found;
        if (level == EAcademicLevel.GRADUATION) {
            found = byCpf ? studentRepository.searchGraduationByCpf(digits, page)
                          : studentRepository.searchGraduationByName(name, page);
        } else {
            found = byCpf ? postgradStudentRepository.searchByCpf(digits, page)
                          : postgradStudentRepository.searchByName(name, page);
        }
        return found.stream()
                .map(s -> new StudentSearchHit(s.getId(), s.getFullName(), maskCpf(s.getCpf()), s.getRegistrationNumber()))
                .toList();
    }

    /** 12345678901 → ***.***.789-01: mostra o bastante para distinguir homônimos. */
    static String maskCpf(String cpf) {
        String digits = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (digits.length() != 11) {
            return "***";
        }
        return "***.***." + digits.substring(6, 9) + "-" + digits.substring(9);
    }
}
