package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.MessageResponse;
import com.portalcursos.ng02.model.DataDeletionRequest;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.DataDeletionRequestRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.service.DataAnonymizationService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints de autoatendimento de privacidade do titular (LGPD art. 18 / GDPR art. 15-17):
 * acesso aos próprios dados e solicitação de eliminação, com análise manual do
 * administrador/secretaria antes de qualquer exclusão definitiva — registros acadêmicos e
 * financeiros podem estar sujeitos a prazo legal de guarda que este sistema não determina
 * automaticamente.
 */
@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyController.class);

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final DataDeletionRequestRepository deletionRequestRepository;
    private final DataAnonymizationService anonymizationService;

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl principal = (UserDetailsImpl) auth.getPrincipal();
        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    @GetMapping("/my-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyData() {
        User user = currentUser();

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("username", user.getUsername());
        account.put("email", user.getEmail());
        account.put("roles", user.getRoles().stream().map(r -> r.getName().name()).toList());
        account.put("privacyConsentAccepted", user.getPrivacyConsentAccepted());
        account.put("privacyConsentVersion", user.getPrivacyConsentVersion());
        account.put("privacyConsentAt", user.getPrivacyConsentAt());
        data.put("account", account);

        studentRepository.findByUserId(user.getId()).ifPresent(student -> {
            Map<String, Object> academic = new LinkedHashMap<>();
            academic.put("registrationNumber", student.getRegistrationNumber());
            academic.put("fullName", student.getFullName());
            academic.put("cpf", student.getCpf());
            academic.put("phone", student.getPhone());
            academic.put("dateOfBirth", student.getDateOfBirth());
            academic.put("address", student.getAddress());
            academic.put("enrollmentStatus", student.getEnrollmentStatus());
            academic.put("course", student.getCourse() != null ? student.getCourse().getDenominacaoCurso() : null);
            data.put("academic", academic);
        });

        return ResponseEntity.ok(data);
    }

    @PostMapping("/data-deletion-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> requestDeletion() {
        User user = currentUser();

        Optional<DataDeletionRequest> existing = deletionRequestRepository
                .findFirstByUserIdAndStatus(user.getId(), DataDeletionRequest.Status.PENDING);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Você já possui uma solicitação de eliminação de dados pendente de análise."));
        }

        DataDeletionRequest request = DataDeletionRequest.builder()
                .userId(user.getId())
                .requestedUsername(user.getUsername())
                .status(DataDeletionRequest.Status.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();
        deletionRequestRepository.save(request);

        logger.info("[PRIVACY] Solicitação de eliminação de dados registrada para o usuário id={}", user.getId());
        return ResponseEntity.ok(new MessageResponse(
                "Solicitação registrada. A administração irá analisar e executar a eliminação dos seus dados, observados os prazos legais de guarda."));
    }

    @GetMapping("/data-deletion-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<List<DataDeletionRequest>> listDeletionRequests() {
        return ResponseEntity.ok(deletionRequestRepository.findAllByOrderByRequestedAtDesc());
    }

    @PatchMapping("/data-deletion-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<?> reviewDeletionRequest(@PathVariable Long id, @RequestBody ReviewRequest body) {
        DataDeletionRequest request = deletionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada."));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl reviewer = (UserDetailsImpl) auth.getPrincipal();

        request.setStatus(body.status());
        request.setReviewedBy(reviewer.getUsername());
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes(body.notes());
        deletionRequestRepository.save(request);

        logger.info("[PRIVACY] Solicitação id={} revisada por {} -> {}", id, reviewer.getUsername(), body.status());
        return ResponseEntity.ok(request);
    }

    @PostMapping("/data-deletion-requests/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<?> executeDeletion(@PathVariable Long id) {
        DataDeletionRequest request = deletionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada."));

        if (request.getStatus() != DataDeletionRequest.Status.APPROVED) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Só é possível executar a eliminação em solicitações já aprovadas."));
        }

        User targetUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário da solicitação não encontrado (pode já ter sido eliminado)."));

        DataAnonymizationService.AnonymizationResult result = anonymizationService.anonymize(targetUser);

        if (!result.completed()) {
            return ResponseEntity.status(409).body(new MessageResponse(
                    result.reason() + " Elegível a partir de " + result.earliestEligibleDate() + "."));
        }

        request.setStatus(DataDeletionRequest.Status.COMPLETED);
        String previousNotes = request.getReviewNotes();
        request.setReviewNotes((previousNotes != null && !previousNotes.isBlank() ? previousNotes + " | " : "")
                + "Anonimização executada automaticamente em " + LocalDateTime.now());
        request.setReviewedAt(LocalDateTime.now());
        deletionRequestRepository.save(request);

        logger.info("[PRIVACY] Eliminação definitiva executada para solicitação id={} (usuário id={})", id, targetUser.getId());
        return ResponseEntity.ok(new MessageResponse("Dados eliminados/anonimizados com sucesso."));
    }

    public record ReviewRequest(DataDeletionRequest.Status status, String notes) {}
}
