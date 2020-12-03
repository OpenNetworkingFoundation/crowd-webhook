package ut.org.opennetworking.crowd;

import java.util.List;
import java.util.Map;

import com.atlassian.crowd.audit.AuditLogChangeset;
import com.atlassian.crowd.audit.query.AuditLogQuery;
import com.atlassian.crowd.manager.audit.AuditLogConfiguration;
import com.atlassian.crowd.manager.audit.AuditService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class MockAuditService implements AuditService {

    Map<String, List<AuditLogChangeset>> entries = Maps.newHashMap();

    @Override
    public void saveAudit(AuditLogChangeset auditLogChangeset) {
        entries.compute(auditLogChangeset.getEntityName(), (k, v) -> {
            if (v == null) {
                return Lists.newArrayList(auditLogChangeset);
            } else {
                v.add(auditLogChangeset);
                return v;
            }
        });
    }

    @Override
    public <RESULT> List<RESULT> searchAuditLog(AuditLogQuery<RESULT> auditLogQuery) {
        String user = auditLogQuery.getUsers().iterator().next().getName();
        return (List<RESULT>) entries.getOrDefault(user, Lists.newArrayList());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean shouldAuditEvent() {
        return true;
    }

    // ---------------------

    @Override
    public AuditLogConfiguration getConfiguration() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void saveConfiguration(AuditLogConfiguration auditLogConfiguration) {
        // TODO Auto-generated method stub
    }
}
