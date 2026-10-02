package com.portalcursos.tokencontrol.repository;

import com.portalcursos.tokencontrol.model.UsageEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageRepository extends JpaRepository<UsageEntry, Long> {

    @Query("select e.messageId from UsageEntry e where e.messageId in :ids")
    Set<String> findExistingMessageIds(@Param("ids") Collection<String> ids);

    // Agrupa por hora (UTC); o rollup por dia do ciclo é feito no service.
    @Query("""
        select new com.portalcursos.tokencontrol.repository.HourUsage(
            e.hourBucket, sum(e.inputTokens), sum(e.outputTokens),
            sum(e.cacheCreationTokens), sum(e.cacheReadTokens), count(e))
        from UsageEntry e where e.occurredAt >= :start and e.occurredAt < :end
        group by e.hourBucket order by e.hourBucket""")
    List<HourUsage> aggregateByHour(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        select new com.portalcursos.tokencontrol.repository.UsageAggregate(
            e.process, sum(e.inputTokens), sum(e.outputTokens),
            sum(e.cacheCreationTokens), sum(e.cacheReadTokens), count(e))
        from UsageEntry e where e.occurredAt >= :start and e.occurredAt < :end
        group by e.process""")
    List<UsageAggregate> aggregateByProcess(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        select new com.portalcursos.tokencontrol.repository.UsageAggregate(
            e.model, sum(e.inputTokens), sum(e.outputTokens),
            sum(e.cacheCreationTokens), sum(e.cacheReadTokens), count(e))
        from UsageEntry e where e.occurredAt >= :start and e.occurredAt < :end
        group by e.model""")
    List<UsageAggregate> aggregateByModel(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        select new com.portalcursos.tokencontrol.repository.UsageAggregate(
            'total', coalesce(sum(e.inputTokens), 0L), coalesce(sum(e.outputTokens), 0L),
            coalesce(sum(e.cacheCreationTokens), 0L), coalesce(sum(e.cacheReadTokens), 0L), count(e))
        from UsageEntry e where e.occurredAt >= :start and e.occurredAt < :end""")
    UsageAggregate total(@Param("start") Instant start, @Param("end") Instant end);
}
