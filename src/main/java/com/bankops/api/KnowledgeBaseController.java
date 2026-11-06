package com.bankops.api;

import com.bankops.application.cases.PageResponse;
import com.bankops.application.knowledge.KnowledgeBaseItem;
import com.bankops.application.knowledge.KnowledgeBaseService;
import com.bankops.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    @GetMapping
    PageResponse<KnowledgeBaseItem> list(@RequestParam(required = false) ErrorCode errorCode,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer pageSize) {
        return service.search(errorCode, search, page, pageSize);
    }
}
