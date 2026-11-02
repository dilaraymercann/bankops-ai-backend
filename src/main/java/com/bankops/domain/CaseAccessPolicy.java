package com.bankops.domain;

/** Kayıt görüntüleme yetkisi (CLAUDE.md §5): uzman yalnızca kendine atananı, takım lideri hepsini görür, yönetici kayıt görmez. */
public final class CaseAccessPolicy {

    public boolean canView(Role role, Long userId, Long assignedToUserId) {
        return switch (role) {
            case TeamLead -> true;
            case Specialist -> userId.equals(assignedToUserId);
            case Manager -> false;
        };
    }

    public void assertCanView(Role role, Long userId, Long assignedToUserId) {
        if (!canView(role, userId, assignedToUserId)) {
            throw new ForbiddenOperationException("Bu kaydı görüntüleme yetkiniz yok");
        }
    }
}
