package com.bankops.application.knowledge;

import com.bankops.application.cases.PageResponse;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.TransactionType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Bilgi tabanı listesi (CLAUDE.md §12.4): doğrulanmış ve bilgi tabanına alınmış çözümler. Sorgu raporlama niteliğinde
 * olduğundan (birleştirme + sayım) JPA yerine okunabilir bir native SQL'dir.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String FROM = """
            FROM resolutions r
            JOIN exception_cases c ON c.id = r.case_id
            JOIN payment_transactions t ON t.id = c.transaction_id
            JOIN users proposer ON proposer.id = r.proposed_by_user_id
            JOIN users approver ON approver.id = r.approved_by_user_id
            WHERE r.is_verified = TRUE AND r.include_in_knowledge_base = TRUE
            """;

    private final JdbcTemplate jdbc;

    public PageResponse<KnowledgeBaseItem> search(ErrorCode errorCode, String search, Integer page, Integer pageSize) {
        int safePage = Math.max(0, page == null ? 0 : page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize));

        StringBuilder filter = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        if (errorCode != null) {
            filter.append(" AND c.error_code = ?");
            arguments.add(errorCode.name());
        }
        if (search != null && !search.isBlank()) {
            filter.append(" AND (r.text ILIKE ? OR c.case_number ILIKE ?)");
            String pattern = "%" + search.trim() + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }

        Long total = jdbc.queryForObject("SELECT count(*) " + FROM + filter, Long.class, arguments.toArray());
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(safeSize);
        pageArguments.add(safePage * safeSize);
        List<KnowledgeBaseItem> items = jdbc.query("""
                SELECT c.id, c.case_number, c.error_code, t.type, r.text,
                       proposer.full_name AS proposed_by, approver.full_name AS approved_by, r.approved_at,
                       (SELECT count(*) FROM ai_analyses a
                         WHERE a.referenced_case_ids @> jsonb_build_array(c.case_number)) AS ai_reference_count
                """ + FROM + filter + " ORDER BY r.approved_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (row, index) -> new KnowledgeBaseItem(
                        row.getLong("id"), row.getString("case_number"), ErrorCode.valueOf(row.getString("error_code")),
                        TransactionType.valueOf(row.getString("type")), row.getString("text"),
                        row.getString("proposed_by"), row.getString("approved_by"),
                        row.getTimestamp("approved_at").toInstant(), row.getLong("ai_reference_count")),
                pageArguments.toArray());

        long totalItems = total == null ? 0 : total;
        return new PageResponse<>(items, safePage, safeSize, totalItems, (int) Math.ceil(totalItems / (double) safeSize));
    }
}
