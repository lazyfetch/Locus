package com.lazyfetch.locus.data.mf.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.lazyfetch.locus.data.mf.dto.LatestNavResponse;
import com.lazyfetch.locus.data.mf.dto.MfApiScheme;

@Service
public class MfApiService {
    private final WebClient webClient;

    public MfApiService(@Qualifier("mfApiWebClient") WebClient webClient)
    {
        this.webClient=webClient;
    }

    public List <MfApiScheme> getAllSchemes()
    {
        return webClient.get()
                    .uri("/mf")
                    .retrieve()
                    .bodyToFlux(MfApiScheme.class)
                    .collectList()
                    .block();
    }

    public LatestNavResponse getLatestNav(int SchemeCode)
    {
        try
        {
            return webClient.get()
                    .uri("/mf/{SchemeCode}/latest",SchemeCode)
                    .retrieve()
                    .bodyToMono(LatestNavResponse.class)
                    .block();
        }

        catch(Exception e)
        {
            log.error("Failed to fetch NAV for scheme {}",SchemeCode,e);
            return null;
        }
    }
}
