package com.portalcursos.ng02.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private CorrelationIdInterceptor correlationIdInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationIdInterceptor);
    }

    // Resource handler estático de "/uploads/**" removido: arquivos agora são
    // servidos via DocumentController (/api/uploads/**), que exige autenticação
    // e valida a role do usuário por categoria antes de liberar o arquivo.
}
