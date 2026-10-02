package com.portalcursos.tokencontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "token_plan_config")
public class PlanConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "plan_name", nullable = false, length = 40)
    private String planName;

    @Column(name = "reset_day_of_week", nullable = false)
    private short resetDayOfWeek;

    @Column(name = "reset_time", nullable = false)
    private LocalTime resetTime;

    @Column(nullable = false, length = 60)
    private String timezone;

    @Column(name = "weekly_limit_tokens", nullable = false)
    private long weeklyLimitTokens;

    @Column(name = "count_cache_reads", nullable = false)
    private boolean countCacheReads;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanConfig() {}

    public PlanConfig(String planName, int resetDayOfWeek, LocalTime resetTime, String timezone,
            long weeklyLimitTokens, boolean countCacheReads, Instant updatedAt) {
        apply(planName, resetDayOfWeek, resetTime, timezone, weeklyLimitTokens, countCacheReads, updatedAt);
    }

    public void apply(String planName, int resetDayOfWeek, LocalTime resetTime, String timezone,
            long weeklyLimitTokens, boolean countCacheReads, Instant updatedAt) {
        this.planName = planName;
        this.resetDayOfWeek = (short) resetDayOfWeek;
        this.resetTime = resetTime;
        this.timezone = timezone;
        this.weeklyLimitTokens = weeklyLimitTokens;
        this.countCacheReads = countCacheReads;
        this.updatedAt = updatedAt;
    }

    public String getPlanName() { return planName; }
    public int getResetDayOfWeek() { return resetDayOfWeek; }
    public LocalTime getResetTime() { return resetTime; }
    public String getTimezone() { return timezone; }
    public long getWeeklyLimitTokens() { return weeklyLimitTokens; }
    public boolean isCountCacheReads() { return countCacheReads; }
    public Instant getUpdatedAt() { return updatedAt; }
}
