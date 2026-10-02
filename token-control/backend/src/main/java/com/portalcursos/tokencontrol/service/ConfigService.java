package com.portalcursos.tokencontrol.service;

import com.portalcursos.tokencontrol.dto.ConfigDto;
import com.portalcursos.tokencontrol.exception.BusinessException;
import com.portalcursos.tokencontrol.model.PlanConfig;
import com.portalcursos.tokencontrol.repository.PlanConfigRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {

    private final PlanConfigRepository repository;
    private final Clock clock;

    public ConfigService(PlanConfigRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanConfig current() {
        return repository.findById(PlanConfig.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("token_plan_config sem linha id=1 (migration V1)"));
    }

    @Transactional
    public PlanConfig update(ConfigDto dto) {
        try {
            ZoneId.of(dto.timezone());
        } catch (DateTimeException e) {
            throw new BusinessException("Fuso horário inválido: " + dto.timezone());
        }
        PlanConfig config = current();
        config.apply(dto.planName().trim(), dto.resetDayOfWeek(), LocalTime.parse(dto.resetTime()), dto.timezone(),
                dto.weeklyLimitTokens(), dto.monthlyLimitTokens(), dto.countCacheReads(), clock.instant());
        return repository.save(config);
    }

    public static ConfigDto toDto(PlanConfig c) {
        return new ConfigDto(c.getPlanName(), c.getResetDayOfWeek(),
                String.format("%02d:%02d", c.getResetTime().getHour(), c.getResetTime().getMinute()),
                c.getTimezone(), c.getWeeklyLimitTokens(), c.getMonthlyLimitTokens(), c.isCountCacheReads());
    }
}
