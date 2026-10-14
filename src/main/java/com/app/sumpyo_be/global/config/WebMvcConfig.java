package com.app.sumpyo_be.global.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConverters(converter -> {
            if (converter instanceof JacksonJsonHttpMessageConverter jacksonConverter) {
                jacksonConverter.setSupportedMediaTypes(withOctetStream(jacksonConverter.getSupportedMediaTypes()));
            }
        });
    }

    private List<MediaType> withOctetStream(List<MediaType> mediaTypes) {
        List<MediaType> result = new ArrayList<>(mediaTypes);
        result.add(MediaType.APPLICATION_OCTET_STREAM);
        return result;
    }
}
