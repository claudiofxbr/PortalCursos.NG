package com.portalcursos.tokencontrol.controller;

import com.portalcursos.tokencontrol.dto.ConfigDto;
import com.portalcursos.tokencontrol.dto.HistoryEntry;
import com.portalcursos.tokencontrol.dto.IngestResult;
import com.portalcursos.tokencontrol.dto.SummaryResponse;
import com.portalcursos.tokencontrol.dto.UsageBatchRequest;
import com.portalcursos.tokencontrol.service.ConfigService;
import com.portalcursos.tokencontrol.service.IngestService;
import com.portalcursos.tokencontrol.neon.DbStatus;
import com.portalcursos.tokencontrol.neon.DbStatusService;
import com.portalcursos.tokencontrol.service.SummaryService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final IngestService ingestService;
    private final SummaryService summaryService;
    private final ConfigService configService;
    private final DbStatusService dbStatusService;
    private final Clock clock;

    public TokenController(IngestService ingestService, SummaryService summaryService, ConfigService configService,
            DbStatusService dbStatusService, Clock clock) {
        this.dbStatusService = dbStatusService;
        this.ingestService = ingestService;
        this.summaryService = summaryService;
        this.configService = configService;
        this.clock = clock;
    }

    @PostMapping("/usage")
    public IngestResult ingest(@Valid @RequestBody UsageBatchRequest request) {
        return ingestService.ingest(request.entries());
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return summaryService.summary(clock.instant());
    }

    @GetMapping("/history")
    public List<HistoryEntry> history(@RequestParam(defaultValue = "8") int cycles) {
        int bounded = Math.min(Math.max(cycles, 1), 26);
        return summaryService.history(clock.instant(), bounded);
    }

    @GetMapping("/db")
    public DbStatus db() {
        return dbStatusService.status();
    }

    @GetMapping("/config")
    public ConfigDto config() {
        return ConfigService.toDto(configService.current());
    }

    @PutMapping("/config")
    public ConfigDto updateConfig(@Valid @RequestBody ConfigDto dto) {
        return ConfigService.toDto(configService.update(dto));
    }
}
