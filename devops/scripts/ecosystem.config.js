module.exports = {
  apps: [
    {
      name: 'portalcursos-backend',
      cwd: './backend',
      script: 'java',
      args: [
        '-Xmx512M',
        '-jar',
        'target/portalcursos-backend.jar',
        '--server.port=8080'
      ],
      env: {
        NODE_ENV: 'production',
        SPRING_PROFILES_ACTIVE: 'prod'
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: './logs/backend-error.log',
      out_file: './logs/backend-out.log',
      autorestart: true,
      watch: false,
      max_memory_restart: '750M'
    },
    {
      name: 'portalcursos-frontend',
      cwd: './frontend',
      script: 'npm',
      args: 'start',
      instances: 'max',
      exec_mode: 'cluster',
      env: {
        NODE_ENV: 'production',
        PORT: 3000
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: './logs/frontend-error.log',
      out_file: './logs/frontend-out.log',
      autorestart: true,
      watch: false,
      max_memory_restart: '600M'
    }
  ]
};
