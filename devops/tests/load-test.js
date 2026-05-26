import http from 'k6/http';
import { check, sleep } from 'k6';

// Configurações de carga do teste de estresse
export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Rampa de subida para 20 usuários virtuais (VUs)
    { duration: '1m', target: 20 },  // Mantém 20 usuários por 1 minuto
    { duration: '30s', target: 0 },  // Desaceleração
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],   // Falha de requisição deve ser menor que 1%
    http_req_duration: ['p(95)<1500'], // 95% das requisições devem responder em menos de 1.5s
  },
};

const BASE_URL = 'http://localhost:8080/api';

export default function () {
  // 1. Simular requisição ao Health Check
  const healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, {
    'status é 200': (r) => r.status === 200,
    'telemetria ativa': (r) => r.body.includes('status') || r.body.includes('UP') || r.body.includes('resilient'),
  });

  sleep(1);
}
