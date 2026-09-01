package com.portalcursos.ng02.service;

import com.portalcursos.ng02.repository.LoginAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova que LoginAttemptService#loginFailed() serializa corretamente incrementos
 * concorrentes do mesmo IP via lock pessimista (SELECT ... FOR UPDATE), sem lost
 * update. Antes da correção (findById + save sem lock), N threads concorrentes
 * podiam ler o mesmo valor do contador e sobrescrever o incremento umas das
 * outras — o contador final ficava menor que N e o bloqueio por força bruta
 * podia nunca ser acionado mesmo após muito mais que MAX_ATTEMPTS tentativas.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:loginattempttestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    // O pool de producao (application.properties) e propositalmente pequeno (5, dimensionado
    // para o Neon). Esse teste dispara 10 chamadas verdadeiramente simultaneas so para provar
    // que o lock pessimista serializa corretamente — nao e um teste de dimensionamento de pool,
    // entao usamos um pool maior aqui para isolar a variavel sob teste (lock) da contencao de
    // conexao, que teria um efeito de confusao (mascarando lost updates com timeouts de pool).
    "spring.datasource.hikari.maximum-pool-size=20",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
public class LoginAttemptServiceConcurrencyTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    public void concurrentFailedLoginsForSameIpAreSerializedWithoutLostUpdates() throws InterruptedException {
        String ip = "203.0.113.77";
        int threadCount = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        // Sem isso, uma excecao dentro do Runnable (ex.: timeout de conexao sob CI mais lento)
        // e engolida silenciosamente pelo ExecutorService — o doneLatch.countDown() do finally
        // mascara a falha e o teste passa a medir um contador subestimado sem saber por que.
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    loginAttemptService.loginFailed(ip);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Timeout aguardando as threads ficarem prontas");
        startLatch.countDown(); // libera todas as threads ao mesmo tempo
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Timeout aguardando as tentativas concorrentes");
        pool.shutdown();

        assertTrue(failures.isEmpty(),
                "Nenhuma thread deveria falhar; falhas: " + failures);

        var record = loginAttemptRepository.findById(ip).orElse(null);
        assertNotNull(record, "Registro de tentativas deveria existir após as falhas de login");
        assertEquals(threadCount, record.getAttempts(),
                "Contador de tentativas deveria refletir exatamente as " + threadCount + " falhas concorrentes, sem lost update");
        assertNotNull(record.getBlockedUntil(),
                "Com " + threadCount + " tentativas (>= MAX_ATTEMPTS), o IP deveria estar bloqueado");
    }
}
