package com.bankops.application.approvals;

/** Bir çözüm bilgi tabanına girmek üzere onaylandı; embedding'i işlem tamamlandıktan sonra hesaplanır. */
public record ResolutionApprovedEvent(Long resolutionId) {
}
