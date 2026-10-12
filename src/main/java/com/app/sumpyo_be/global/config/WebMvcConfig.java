package com.app.sumpyo_be.global.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setSupportedMediaTypes(withOctetStream(jacksonConverter.getSupportedMediaTypes()));
            } else if (converter instanceof JacksonJsonHttpMessageConverter jacksonConverter) {
                jacksonConverter.setSupportedMediaTypes(withOctetStream(jacksonConverter.getSupportedMediaTypes()));
            }
        }
    }

    private List<MediaType> withOctetStream(List<MediaType> mediaTypes) {
        List<MediaType> result = new ArrayList<>(mediaTypes);
        result.add(MediaType.APPLICATION_OCTET_STREAM);
        return result;
    }
}
