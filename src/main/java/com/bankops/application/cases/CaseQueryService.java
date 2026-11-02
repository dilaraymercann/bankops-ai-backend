package com.bankops.application.cases;

import com.bankops.application.CurrentUser;
import com.bankops.application.ResourceNotFoundException;
import com.bankops.application.auth.UserSummary;
import com.bankops.application.repository.AuditLogRepository;
import com.bankops.application.repository.CaseNoteRepository;
import com.bankops.application.repository.ExceptionCaseRepository;
import com.bankops.application.repository.PaymentTransactionRepository;
import com.bankops.application.repository.StatusHistoryRepository;
import com.bankops.application.repository.UserRepository;
import com.bankops.domain.AuditLog;
import com.bankops.domain.CaseAccessPolicy;
import com.bankops.domain.CaseNote;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.PaymentTransaction;
import com.bankops.domain.SlaCalculator;
import com.bankops.domain.SlaState;
import com.bankops.domain.StatusHistory;
import com.bankops.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kuyruk ve kayıt detayı sorguları (Faz 2, salt okunur). Yazma işlemleri Faz 3'te eklenecek. */
@Service
@RequiredArgsConstructor
public class CaseQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("slaDeadline", "createdAt", "caseNumber");
    private static final Pattern ESCALATED_TO = Pattern.compile("\"escalatedTo\"\\s*:\\s*(\\d+)");

    private final ExceptionCaseRepository caseRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CaseNoteRepository noteRepository;
    private final StatusHistoryRepository historyRepository;
    private final AuditLogRepository auditLogRepository;
    private final SlaCalculator slaCalculator;
    private final CaseAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CaseListItem> search(CaseSearchCriteria criteria, CurrentUser user) {
        int page = Math.max(0, criteria.page() == null ? 0 : criteria.page());
        int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, criteria.pageSize() == null ? DEFAULT_PAGE_SIZE : criteria.pageSize()));
        Sort sort = parseSort(criteria.sort());
        var specification = CaseSpecifications.matching(criteria, user);
        Instant now = clock.instant();

        List<ExceptionCase> cases;
        long total;
        if (criteria.slaState() == null) {
            Page<ExceptionCase> result = caseRepository.findAll(specification, PageRequest.of(page, pageSize, sort));
            cases = result.getContent();
            total = result.getTotalElements();
        } else {
            // SLA durumu mesai saatlerine göre hesaplandığı için SQL'de değil bellekte filtrelenir (açık kayıt sayısı küçüktür)
            List<ExceptionCase> matching = caseRepository.findAll(specification, sort).stream()
                    .filter(c -> hasSlaState(c, criteria.slaState(), now))
                    .toList();
            total = matching.size();
            int from = Math.min(page * pageSize, matching.size());
            cases = matching.subList(from, Math.min(from + pageSize, matching.size()));
        }
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        return new PageResponse<>(toListItems(cases, now), page, pageSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public CaseSummary summary(CurrentUser user) {
        Instant now = clock.instant();
        List<ExceptionCase> open = caseRepository.findAll(CaseSpecifications.matching(CaseSearchCriteria.none(), user));
        long breached = open.stream().filter(c -> hasSlaState(c, SlaState.Breached, now)).count();
        long approaching = open.stream().filter(c -> hasSlaState(c, SlaState.Approaching, now)).count();
        long awaitingReview = open.stream()
                .filter(c -> c.getStatus() == com.bankops.domain.CaseStatus.New || c.getStatus() == com.bankops.domain.CaseStatus.Assigned)
                .count();
        long inReview = open.stream().filter(c -> c.getStatus() == com.bankops.domain.CaseStatus.InReview).count();
        return new CaseSummary(open.size(), breached, approaching, awaitingReview, inReview);
    }

    @Transactional(readOnly = true)
    public CaseDetail get(Long id, CurrentUser user) {
        ExceptionCase exceptionCase = caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kayıt bulunamadı"));
        accessPolicy.assertCanView(user.role(), user.id(), exceptionCase.getAssignedToUserId());

        PaymentTransaction transaction = transactionRepository.findById(exceptionCase.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("İşlem bulunamadı"));
        List<CaseNote> notes = noteRepository.findByCaseIdOrderByCreatedAtAsc(id);
        List<StatusHistory> statusHistory = historyRepository.findByCaseIdOrderByChangedAtAsc(id);
        List<AuditLog> escalations = auditLogRepository
                .findByEntityTypeAndEntityIdAndActionOrderByTimestampAsc("ExceptionCase", id, "ESCALATE");

        Map<Long, User> users = loadUsers(exceptionCase, notes, statusHistory, escalations);
        User assignee = users.get(exceptionCase.getAssignedToUserId());

        return new CaseDetail(
                exceptionCase.getId(),
                exceptionCase.getCaseNumber(),
                exceptionCase.getErrorCode(),
                exceptionCase.getErrorDescription(),
                exceptionCase.getPriority(),
                exceptionCase.getStatus(),
                exceptionCase.isEscalated(),
                exceptionCase.getSlaDeadline(),
                slaView(exceptionCase, clock.instant()),
                exceptionCase.getCreatedAt(),
                exceptionCase.getUpdatedAt(),
                exceptionCase.getClosedAt(),
                assignee == null ? null : UserSummary.from(assignee),
                toTransactionView(transaction),
                notes.stream().map(n -> new NoteView(n.getId(), nameOf(users, n.getAuthorUserId()), n.getText(), n.getCreatedAt())).toList(),
                buildHistory(statusHistory, escalations, users));
    }

    // ---------------------------------------------------------------- SLA

    private boolean hasSlaState(ExceptionCase exceptionCase, SlaState state, Instant now) {
        SlaView view = slaView(exceptionCase, now);
        return view != null && view.state() == state;
    }

    /** Kapalı kayıtlarda SLA gösterilmez (null). */
    private SlaView slaView(ExceptionCase c, Instant now) {
        if (!c.getStatus().isOpen()) {
            return null;
        }
        SlaState state = slaCalculator.stateAt(c.getPriority(), c.getCreatedAt(), c.getSlaDeadline(), now);
        Duration remaining = state == SlaState.Breached
                ? slaCalculator.workingTimeBetween(c.getPriority(), c.getSlaDeadline(), now).negated()
                : slaCalculator.remaining(c.getPriority(), now, c.getSlaDeadline());
        Duration total = slaCalculator.workingTimeBetween(c.getPriority(), c.getCreatedAt(), c.getSlaDeadline());
        Duration elapsed = slaCalculator.workingTimeBetween(c.getPriority(), c.getCreatedAt(), now);
        double progress = total.isZero() ? 1.0 : Math.min(1.0, (double) elapsed.toMillis() / total.toMillis());
        return new SlaView(state, remaining.toMinutes(), progress);
    }

    // ---------------------------------------------------------------- dönüşümler

    private List<CaseListItem> toListItems(List<ExceptionCase> cases, Instant now) {
        Map<Long, PaymentTransaction> transactions = transactionRepository
                .findAllById(cases.stream().map(ExceptionCase::getTransactionId).toList()).stream()
                .collect(Collectors.toMap(PaymentTransaction::getId, Function.identity()));
        Map<Long, User> users = userRepository
                .findAllById(cases.stream().map(ExceptionCase::getAssignedToUserId).filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return cases.stream().map(c -> {
            PaymentTransaction transaction = transactions.get(c.getTransactionId());
            return new CaseListItem(
                    c.getId(), c.getCaseNumber(), transaction.getType(), c.getErrorCode(), c.getErrorDescription(),
                    transaction.getAmount().toPlainString(), transaction.getCurrency(), c.getPriority(), c.getStatus(),
                    c.isEscalated(), c.getSlaDeadline(), slaView(c, now), c.getCreatedAt(), nameOf(users, c.getAssignedToUserId()));
        }).toList();
    }

    private TransactionView toTransactionView(PaymentTransaction t) {
        return new TransactionView(
                t.getReference(), t.getType(), t.getAmount().toPlainString(), t.getCurrency(),
                DisplayMasker.name(t.getSenderName()), DisplayMasker.iban(t.getSenderIban()),
                DisplayMasker.name(t.getReceiverName()), DisplayMasker.iban(t.getReceiverIban()),
                t.getDescription(), t.getChannel(), t.getValueDate(), t.getCreatedAt());
    }

    /** Durum geçişleri ve SLA eskalasyonları tek bir zaman çizelgesinde birleştirilir. */
    private List<HistoryEntry> buildHistory(List<StatusHistory> statusHistory, List<AuditLog> escalations, Map<Long, User> users) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (StatusHistory h : statusHistory) {
            entries.add(new HistoryEntry("STATUS_CHANGE", h.getFromStatus(), h.getToStatus(), h.getReason(),
                    nameOf(users, h.getChangedByUserId()), null, h.getChangedAt()));
        }
        for (AuditLog log : escalations) {
            entries.add(new HistoryEntry("ESCALATION", null, null, null, null,
                    nameOf(users, escalatedTo(log)), log.getTimestamp()));
        }
        entries.sort(Comparator.comparing(HistoryEntry::at));
        return entries;
    }

    private Long escalatedTo(AuditLog log) {
        if (log.getDetails() == null) {
            return null;
        }
        Matcher matcher = ESCALATED_TO.matcher(log.getDetails());
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private Map<Long, User> loadUsers(ExceptionCase c, List<CaseNote> notes, List<StatusHistory> history, List<AuditLog> escalations) {
        Set<Long> ids = new HashSet<>();
        ids.add(c.getAssignedToUserId());
        notes.forEach(n -> ids.add(n.getAuthorUserId()));
        history.forEach(h -> ids.add(h.getChangedByUserId()));
        escalations.forEach(e -> ids.add(escalatedTo(e)));
        ids.remove(null);
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static String nameOf(Map<Long, User> users, Long userId) {
        User user = userId == null ? null : users.get(userId);
        return user == null ? null : user.getFullName();
    }

    /** "slaDeadline,asc" biçimi. Yalnızca izin verilen alanlara sıralanabilir; eşitlikte sayfalamanın tutarlı kalması için id eklenir. */
    private Sort parseSort(String sort) {
        String field = "slaDeadline";
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            field = parts[0].trim();
            if (!SORTABLE_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Geçersiz sıralama alanı: " + field);
            }
            if (parts.length > 1) {
                direction = Sort.Direction.fromString(parts[1].trim());
            }
        }
        return Sort.by(direction, field).and(Sort.by("id"));
    }
}
