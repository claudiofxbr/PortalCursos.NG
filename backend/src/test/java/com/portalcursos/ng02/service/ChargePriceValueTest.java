package com.portalcursos.ng02.service;

import com.portalcursos.ng02.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChargePriceValueTest {

    @Test
    void aceitaZeroEValoresComAteDuasCasas() {
        assertEquals(new BigDecimal("0.00"), ChargePriceService.normalize(BigDecimal.ZERO));
        assertEquals(new BigDecimal("150.00"), ChargePriceService.normalize(new BigDecimal("150")));
        assertEquals(new BigDecimal("150.50"), ChargePriceService.normalize(new BigDecimal("150.5")));
        assertEquals(new BigDecimal("99.99"), ChargePriceService.normalize(new BigDecimal("99.990")));
        assertEquals(new BigDecimal("9999999.99"), ChargePriceService.normalize(new BigDecimal("9999999.99")));
    }

    @Test
    void recusaNuloNegativoTresCasasEAcimaDoLimite() {
        assertThrows(BusinessException.class, () -> ChargePriceService.normalize(null));
        assertThrows(BusinessException.class, () -> ChargePriceService.normalize(new BigDecimal("-0.01")));
        assertThrows(BusinessException.class, () -> ChargePriceService.normalize(new BigDecimal("1.234")));
        assertThrows(BusinessException.class, () -> ChargePriceService.normalize(new BigDecimal("10000000.00")));
    }
}
