# Guia de Execução - PortalCursos.NG (V20.0 ULTRA)

Siga estas etapas para rodar o aplicativo corretamente. 

## 1. Backend (Java Spring Boot)
Abra um terminal e entre na pasta `backend`:

```powershell
# No Windows PowerShell:
cd backend
.\mvnw.cmd spring-boot:run
```

> [!TIP]
> A porta **8080** foi liberada. Se você receber um erro de porta ocupada, o sistema já tentou limpar processos anteriores.

## 2. Frontend (Next.js)
Abra um **segundo terminal** e entre na pasta `frontend`:

```powershell
cd frontend
npm install
npm run dev
```

> [!IMPORTANT]
> O comando `npm` deve ser executado **dentro** da pasta `frontend`.

## 3. Banco de Dados (Neon)
O sistema está configurado para o banco de dados Neon. 
O backend aguardará o banco "acordar" automaticamente.

Acesse: [http://localhost:3000](http://localhost:3000)
