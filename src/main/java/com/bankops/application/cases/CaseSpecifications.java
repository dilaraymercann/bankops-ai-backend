package com.bankops.application.cases;

import com.bankops.application.CurrentUser;
import com.bankops.domain.CaseStatus;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.PaymentTransaction;
import com.bankops.domain.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import org.springframework.data.jpa.domain.Specification;

/** Kuyruk filtrelerini tek bir sorguya çeviren yapı taşları. Görünürlük kuralı da (uzman yalnızca kendi kaydı) burada uygulanır. */
final class CaseSpecifications {

    private static final List<CaseStatus> OPEN_STATUSES =
            Arrays.stream(CaseStatus.values()).filter(CaseStatus::isOpen).toList();

    private CaseSpecifications() {
    }

    static Specification<ExceptionCase> matching(CaseSearchCriteria criteria, CurrentUser user) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (user.role() == Role.Specialist) {
                predicates.add(cb.equal(root.get("assignedToUserId"), user.id()));
            } else if (criteria.assignedTo() != null) {
                predicates.add(cb.equal(root.get("assignedToUserId"), criteria.assignedTo()));
            }

            // Durum filtresi yoksa kuyruk yalnızca açık kayıtları gösterir
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            } else {
                predicates.add(root.get("status").in(OPEN_STATUSES));
            }
            if (criteria.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), criteria.priority()));
            }
            if (criteria.errorCode() != null) {
                predicates.add(cb.equal(root.get("errorCode"), criteria.errorCode()));
            }
            if (criteria.transactionType() != null) {
                predicates.add(transactionMatches(query.subquery(Long.class), cb, root,
                        (tx, builder) -> builder.equal(tx.get("type"), criteria.transactionType())));
            }
            if (criteria.search() != null && !criteria.search().isBlank()) {
                predicates.add(search(criteria.search(), query.subquery(Long.class), cb, root));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Arama: kayıt no, hata açıklaması/kodu, işlem referansı, işlem açıklaması veya tutar. */
    private static Predicate search(String rawTerm, Subquery<Long> subquery, CriteriaBuilder cb, Root<ExceptionCase> root) {
        String term = rawTerm.trim().toLowerCase(Locale.ROOT);
        String like = "%" + escapeLike(term) + "%";

        List<Predicate> onCase = new ArrayList<>();
        onCase.add(cb.like(cb.lower(root.get("caseNumber")), like, '\\'));
        onCase.add(cb.like(cb.lower(root.get("errorDescription")), like, '\\'));
        onCase.add(cb.like(cb.lower(root.get("errorCode").as(String.class)), like, '\\'));

        // "48.750,00" gibi yazılan tutarlar veritabanındaki "48750.00" biçimine çevrilir
        boolean looksLikeAmount = term.matches("[0-9.,]+");
        String amountLike = "%" + term.replace(".", "").replace(',', '.') + "%";
        onCase.add(transactionMatches(subquery, cb, root, (tx, builder) -> {
            List<Predicate> onTransaction = new ArrayList<>();
            onTransaction.add(builder.like(builder.lower(tx.get("description")), like, '\\'));
            onTransaction.add(builder.like(builder.lower(tx.get("reference")), like, '\\'));
            if (looksLikeAmount) {
                onTransaction.add(builder.like(tx.get("amount").cast(String.class), amountLike));
            }
            return builder.or(onTransaction.toArray(Predicate[]::new));
        }));
        return cb.or(onCase.toArray(Predicate[]::new));
    }

    /** "Bu kaydın işlemi şu koşulu sağlıyor mu?" sorusunu alt sorgu (EXISTS) olarak kurar. */
    private static Predicate transactionMatches(Subquery<Long> subquery, CriteriaBuilder cb, Root<ExceptionCase> root,
                                                BiFunction<Root<PaymentTransaction>, CriteriaBuilder, Predicate> condition) {
        Root<PaymentTransaction> transaction = subquery.from(PaymentTransaction.class);
        subquery.select(transaction.get("id")).where(
                cb.equal(transaction.get("id"), root.get("transactionId")),
                condition.apply(transaction, cb));
        return cb.exists(subquery);
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
