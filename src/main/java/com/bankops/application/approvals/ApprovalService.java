package com.bankops.application.approvals;

import static com.bankops.domain.CaseStatus.Closed;
import static com.bankops.domain.CaseStatus.InReview;
import static com.bankops.domain.CaseStatus.SolutionProposed;

import com.bankops.application.CurrentUser;
import com.bankops.application.ResourceNotFoundException;
import com.bankops.application.audit.AuditDetails;
import com.bankops.application.audit.AuditService;
import com.bankops.application.cases.CaseDetail;
import com.bankops.application.cases.CaseQueryService;
import com.bankops.application.cases.CaseStatusChanger;
import com.bankops.application.repository.AiAnalysisRepository;
import com.bankops.application.repository.AuditLogRepository;
import com.bankops.application.repository.ExceptionCaseRepository;
import com.bankops.application.repository.PaymentTransactionRepository;
import com.bankops.application.repository.ResolutionRepository;
import com.bankops.application.repository.UserRepository;
import com.bankops.domain.AiAnalysis;
import com.bankops.domain.ApprovalPolicy;
import com.bankops.domain.AuditLog;
import com.bankops.domain.CaseStateMachine;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.PaymentTransaction;
import com.bankops.domain.Resolution;
import com.bankops.domain.SlaCalculator;
import com.bankops.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takım lideri onay akışı (CLAUDE.md §12.3): bekleyen çözümler, inceleme, onay ve revizyon isteği. Dört göz kuralı burada uygulanır. */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ExceptionCaseRepository caseRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ResolutionRepository resolutionRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CaseStatusChanger statusChanger;
    private final AuditService auditService;
    private final CaseStateMachine stateMachine;
    private final ApprovalPolicy approvalPolicy;
    private final SlaCalculator slaCalculator;
    private final CaseQueryService queryService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ApprovalItem> pending() {
        List<ExceptionCase> cases = caseRepository.findByStatusOrderByUpdatedAtAsc(SolutionProposed);
        List<Long> caseIds = cases.stream().map(ExceptionCase::getId).toList();
        Map<Long, Resolution> resolutions = latestPendingResolutions(caseIds);
        Map<Long, PaymentTransaction> transactions = transactionRepository
                .findAllById(cases.stream().map(ExceptionCase::getTransactionId).toList()).stream()
                .collect(Collectors.toMap(PaymentTransaction::getId, Function.identity()));
        Map<Long, User> users = usersById(resolutions.values().stream().map(Resolution::getProposedByUserId).toList());
        Instant now = clock.instant();

        return cases.stream()
                .filter(c -> resolutions.containsKey(c.getId()))
                .map(c -> {
                    Resolution resolution = resolutions.get(c.getId());
                    PaymentTransaction transaction = transactions.get(c.getTransactionId());
                    return new ApprovalItem(c.getId(), c.getCaseNumber(), c.getErrorCode(), c.getErrorDescription(), transaction.getType(),
                            transaction.getAmount().toPlainString(), transaction.getCurrency(),
                            nameOf(users, resolution.getProposedByUserId()), resolution.getCreatedAt(),
                            c.getSlaDeadline().isBefore(now));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalReview review(Long caseId, CurrentUser user) {
        ExceptionCase exceptionCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Kayıt bulunamadı"));
        stateMachine.validate(exceptionCase.getStatus(), Closed);
        Resolution resolution = pendingResolution(caseId);

        AiAnalysis analysis = aiAnalysisRepository.findFirstByCaseIdOrderByCreatedAtDesc(caseId).orElse(null);
        List<AuditLog> recent = auditLogRepository.findTop5ByEntityTypeAndEntityIdOrderByTimestampDesc("ExceptionCase", caseId);

        Set<Long> userIds = new HashSet<>();
        userIds.add(resolution.getProposedByUserId());
        recent.forEach(log -> userIds.add(log.getUserId()));
        Map<Long, User> users = usersById(userIds);

        String proposer = nameOf(users, resolution.getProposedByUserId());
        boolean passed = !user.id().equals(resolution.getProposedByUserId());
        FourEyesCheck fourEyes = new FourEyesCheck(passed, passed
                ? "Çözümü " + proposer + " önerdi, onaylayan siz olacaksınız."
                : "Çözümü siz önerdiniz, kendi önerdiğiniz çözümü onaylayamazsınız.");

        long resolutionMinutes = slaCalculator.workingTimeBetween(
                exceptionCase.getPriority(), exceptionCase.getCreatedAt(), resolution.getCreatedAt()).toMinutes();
        List<AuditLine> audit = recent.stream()
                .map(log -> new AuditLine(log.getTimestamp(), usernameOf(users, log.getUserId()), log.getAction(), log.getDetails()))
                .toList();

        return new ApprovalReview(
                caseId, exceptionCase.getCaseNumber(), exceptionCase.getErrorCode(), exceptionCase.getPriority(),
                resolution.getText(), proposer, resolution.getCreatedAt(), fourEyes,
                analysis != null, analysis == null ? null : analysis.getFeedback(), resolutionMinutes, audit);
    }

    /** Çözümü onaylar: kayıt kapanır, çözüm doğrulanır (ve istenirse bilgi tabanına girer). */
    @Transactional
    public CaseDetail approve(Long caseId, ApproveRequest request, CurrentUser user) {
        ExceptionCase exceptionCase = lockPending(caseId);
        Resolution resolution = pendingResolution(caseId);
        // Dört göz + rol kuralı (domain): öneren kişi onaylayamaz, yalnızca takım lideri onaylar
        approvalPolicy.assertCanApprove(user.role(), user.id(), resolution.getProposedByUserId());

        String note = clean(request.note());
        Instant now = clock.instant();
        statusChanger.change(exceptionCase, Closed, user.id(), note == null ? "Çözüm onaylandı" : note);
        exceptionCase.setClosedByUserId(user.id());
        exceptionCase.setClosedAt(now);

        resolution.setApprovedByUserId(user.id());
        resolution.setApprovedAt(now);
        resolution.setVerified(true);
        resolution.setIncludeInKnowledgeBase(request.includeInKnowledgeBase());

        auditService.record(user.id(), "APPROVE", "ExceptionCase", caseId,
                AuditDetails.of("resolutionId", resolution.getId(), "includeInKnowledgeBase", request.includeInKnowledgeBase(), "note", note));
        if (request.includeInKnowledgeBase()) {
            // Embedding COMMIT'ten sonra hesaplanır (bkz. ResolutionEmbeddingListener)
            eventPublisher.publishEvent(new ResolutionApprovedEvent(resolution.getId()));
        }
        return queryService.get(caseId, user);
    }

    /** Çözümü reddedip kaydı uzmana geri gönderir; not zorunludur. */
    @Transactional
    public CaseDetail requestRevision(Long caseId, RevisionRequest request, CurrentUser user) {
        approvalPolicy.assertRevisionNoteProvided(request.note());
        ExceptionCase exceptionCase = lockPending(caseId);
        Resolution resolution = pendingResolution(caseId);
        approvalPolicy.assertCanApprove(user.role(), user.id(), resolution.getProposedByUserId());

        String note = request.note().trim();
        statusChanger.change(exceptionCase, InReview, user.id(), "Revizyon: " + note);
        auditService.record(user.id(), "REVISION_REQUEST", "ExceptionCase", caseId,
                AuditDetails.of("resolutionId", resolution.getId(), "note", note));
        return queryService.get(caseId, user);
    }

    // ---------------------------------------------------------------- yardımcılar

    private ExceptionCase lockPending(Long caseId) {
        ExceptionCase exceptionCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Kayıt bulunamadı"));
        stateMachine.validate(exceptionCase.getStatus(), Closed);   // onay beklemiyorsa 409
        return exceptionCase;
    }

    private Resolution pendingResolution(Long caseId) {
        return resolutionRepository.findFirstByCaseIdAndVerifiedFalseOrderByCreatedAtDesc(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Onay bekleyen çözüm bulunamadı"));
    }

    private Map<Long, Resolution> latestPendingResolutions(Collection<Long> caseIds) {
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        return resolutionRepository.findByCaseIdInAndVerifiedFalse(caseIds).stream()
                .collect(Collectors.toMap(Resolution::getCaseId, Function.identity(),
                        (first, second) -> first.getCreatedAt().isAfter(second.getCreatedAt()) ? first : second));
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        Set<Long> cleaned = new HashSet<>(ids);
        cleaned.remove(null);
        return userRepository.findAllById(cleaned).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static String nameOf(Map<Long, User> users, Long userId) {
        User user = userId == null ? null : users.get(userId);
        return user == null ? null : user.getFullName();
    }

    private static String usernameOf(Map<Long, User> users, Long userId) {
        User user = userId == null ? null : users.get(userId);
        return user == null ? "sistem" : user.getUsername();
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
