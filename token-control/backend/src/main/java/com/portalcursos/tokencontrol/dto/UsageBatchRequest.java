package com.portalcursos.tokencontrol.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UsageBatchRequest(@NotEmpty @Size(max = 1000) List<@Valid UsageEntryRequest> entries) {}
