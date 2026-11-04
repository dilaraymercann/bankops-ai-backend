package com.bankops.application.cases;

import static com.bankops.domain.CaseStatus.Assigned;
import static com.bankops.domain.CaseStatus.Escalated;
import static com.bankops.domain.CaseStatus.InReview;
import static com.bankops.domain.CaseStatus.New;
import static com.bankops.domain.CaseStatus.Rejected;
import static com.bankops.domain.CaseStatus.SolutionProposed;

import com.bankops.application.CurrentUser;
import com.bankops.application.ResourceNotFoundException;
import com.bankops.application.audit.AuditDetails;
import com.bankops.application.audit.AuditService;
import com.bankops.application.repository.CaseNoteRepository;
import com.bankops.application.repository.ExceptionCaseRepository;
import com.bankops.application.repository.ResolutionRepository;
import com.bankops.application.repository.UserRepository;
import com.bankops.domain.CaseAccessPolicy;
import com.bankops.domain.CaseNote;
import com.bankops.domain.CaseStateMachine;
import com.bankops.domain.CaseStatus;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.ForbiddenOperationException;
import com.bankops.domain.Resolution;
import com.bankops.domain.Role;
import com.bankops.domain.User;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kayıt üzerindeki yazma işlemleri (Faz 3): not, durum geçişi, çözüm önerme, atama. Her metot önce satırı
 * kilitler, yetkiyi kontrol eder, işlemi yapar ve audit'e yazar; sonuç olarak güncel kayıt detayını döner.
 * Onay ve revizyon isteği {@code ApprovalService}'tedir.
 */
@Service
@RequiredArgsConstructor
public class CaseWorkflowService {

    private final ExceptionCaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseNoteRepository noteRepository;
    private final ResolutionRepository resolutionRepository;
    private final CaseStatusChanger statusChanger;
    private final AuditService auditService;
    private final CaseStateMachine stateMachine;
    private final CaseAccessPolicy accessPolicy;
    private final CaseQueryService queryService;
    private final Clock clock;

    @Transactional
    public CaseDetail addNote(Long caseId, AddNoteRequest request, CurrentUser user) {
        ExceptionCase exceptionCase = lockAndAuthorize(caseId, user);
        if (!exceptionCase.getStatus().isOpen()) {
            throw new IllegalArgumentException("Kapalı kayıtlara not eklenemez");
        }
        CaseNote note = new CaseNote();
        note.setCaseId(caseId);
        note.setAuthorUserId(user.id());
        note.setText(request.text().trim());
        note.setCreatedAt(clock.instant());
        noteRepository.save(note);
        auditService.record(user.id(), "NOTE_ADD", "ExceptionCase", caseId, AuditDetails.of("noteId", note.getId()));
        return queryService.get(caseId, user);
    }

    @Transactional
    public CaseDetail transition(Long caseId, TransitionRequest request, CurrentUser user) {
        ExceptionCase exceptionCase = lockAndAuthorize(caseId, user);
        CaseStatus from = exceptionCase.getStatus();
        CaseStatus to = request.toStatus();

        stateMachine.validate(from, to);                       // olmayan geçiş: 409
        if (!stateMachine.manualTargets(from).contains(to)) {  // atama, çözüm önerme ve onay kendi uç noktalarından yapılır
            throw new IllegalArgumentException("Bu geçiş durum listesinden değil, ilgili işlem ekranından yapılmalıdır: " + from + " -> " + to);
        }
        if (from == Escalated && user.role() != Role.TeamLead) {
            throw new ForbiddenOperationException("Eskale edilen kaydı yalnızca takım lideri yeniden incelemeye alabilir");
        }
        String reason = request.reason() == null ? null : request.reason().trim();
        if (stateMachine.requiresReason(to) && (reason == null || reason.isEmpty())) {
            throw new IllegalArgumentException("Bu geçiş için gerekçe zorunludur");
        }

        statusChanger.change(exceptionCase, to, user.id(), reason);
        Instant now = clock.instant();
        if (to == Escalated) {
            exceptionCase.setEscalated(true);
        }
        if (to == Rejected) {
            // İade edilen talimat onaya tabi değildir; kayıt burada kapanır
            exceptionCase.setClosedByUserId(user.id());
            exceptionCase.setClosedAt(now);
        }
        return queryService.get(caseId, user);
    }

    @Transactional
    public CaseDetail proposeResolution(Long caseId, ProposeResolutionRequest request, CurrentUser user) {
        ExceptionCase exceptionCase = lockAndAuthorize(caseId, user);
        // Takım lideri çözüm önermez: aksi halde kendi çözümünü onaylayamayıp kayıt kilitlenirdi
        if (user.role() != Role.Specialist || !user.id().equals(exceptionCase.getAssignedToUserId())) {
            throw new ForbiddenOperationException("Çözümü yalnızca kayda atanmış operasyon uzmanı önerebilir");
        }
        statusChanger.change(exceptionCase, SolutionProposed, user.id(), null);

        Resolution resolution = new Resolution();
        resolution.setCaseId(caseId);
        resolution.setText(request.text().trim());
        resolution.setProposedByUserId(user.id());
        resolution.setCreatedAt(clock.instant());
        resolutionRepository.save(resolution);

        exceptionCase.setProposedByUserId(user.id());
        auditService.record(user.id(), "RESOLUTION_PROPOSE", "ExceptionCase", caseId, AuditDetails.of("resolutionId", resolution.getId()));
        return queryService.get(caseId, user);
    }

    @Transactional
    public CaseDetail assign(Long caseId, AssignRequest request, CurrentUser user) {
        if (user.role() != Role.TeamLead) {
            throw new ForbiddenOperationException("Kayıt atama yetkisi yalnızca takım liderindedir");
        }
        ExceptionCase exceptionCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Kayıt bulunamadı"));
        if (!exceptionCase.getStatus().isOpen() || exceptionCase.getStatus() == SolutionProposed) {
            throw new IllegalArgumentException("Bu durumdaki kayıt atanamaz");
        }
        User assignee = userRepository.findById(request.userId())
                .filter(candidate -> candidate.getRole() == Role.Specialist)
                .orElseThrow(() -> new IllegalArgumentException("Geçerli bir operasyon uzmanı seçilmelidir"));

        Long previousAssignee = exceptionCase.getAssignedToUserId();
        exceptionCase.setAssignedToUserId(assignee.getId());
        exceptionCase.setUpdatedAt(clock.instant());
        auditService.record(user.id(), "ASSIGN", "ExceptionCase", caseId, AuditDetails.of("from", previousAssignee, "to", assignee.getId()));

        if (exceptionCase.getStatus() == New) {
            statusChanger.change(exceptionCase, Assigned, user.id(), "Atandı: " + assignee.getFullName());
        } else if (exceptionCase.getStatus() == Escalated) {
            statusChanger.change(exceptionCase, InReview, user.id(), "Yeniden atandı: " + assignee.getFullName());
        }
        return queryService.get(caseId, user);
    }

    /** Satırı kilitler ve kullanıcının bu kaydı görme yetkisini kontrol eder. */
    private ExceptionCase lockAndAuthorize(Long caseId, CurrentUser user) {
        ExceptionCase exceptionCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Kayıt bulunamadı"));
        accessPolicy.assertCanView(user.role(), user.id(), exceptionCase.getAssignedToUserId());
        return exceptionCase;
    }
}
