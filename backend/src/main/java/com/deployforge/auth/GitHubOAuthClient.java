package com.deployforge.auth;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.ExternalServiceException;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.config.HttpClientConfig;
import com.deployforge.github.api.GitHubApiModels.GitHubAccessTokenResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** GitHub OAuth authorization-code flow: builds the authorize URL and exchanges the code. */
@Component
public class GitHubOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthClient.class);

    private final RestClient restClient;
    private final DeployForgeProperties properties;

    public GitHubOAuthClient(
            @Qualifier(HttpClientConfig.GITHUB_OAUTH_CLIENT) RestClient restClient,
            DeployForgeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String callbackUrl() {
        return properties.publicBackendUrl() + "/api/v1/auth/github/callback";
    }

    public URI authorizeUri(String state) {
        DeployForgeProperties.Github github = properties.github();
        String url =
                github.oauthAuthorizeUrl()
                        + "?client_id="
                        + encode(github.clientId())
                        + "&redirect_uri="
                        + encode(callbackUrl())
                        + "&scope="
                        + encode(github.scopes().replace(',', ' '))
                        + "&state="
                        + encode(state)
                        + "&allow_signup=true";
        return URI.create(url);
    }

    /**
     * Exchanges an authorization code for an access token.
     *
     * @throws BadRequestException when GitHub reports a problem with the code (expired, reused,
     *     mismatched redirect)
     */
    public GitHubAccessTokenResponse exchangeCode(String code) {
        DeployForgeProperties.Github github = properties.github();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", github.clientId());
        form.add("client_secret", github.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", callbackUrl());

        GitHubAccessTokenResponse response;
        try {
            response =
                    restClient
                            .post()
                            .uri(github.oauthTokenUrl())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(GitHubAccessTokenResponse.class);
        } catch (RestClientException e) {
            log.warn("github_token_exchange_transport_failure reason={}", e.getMessage());
            throw new ExternalServiceException(
                    ErrorCode.GITHUB_OAUTH_ERROR, "GitHub could not be reached to complete the login", e);
        }

        if (response == null) {
            throw new ExternalServiceException(
                    ErrorCode.GITHUB_OAUTH_ERROR, "GitHub returned an empty token response");
        }
        if (response.error() != null) {
            // error_description is GitHub's own text and safe to surface; it is genuinely useful
            // ("The code passed is incorrect or expired.").
            log.warn("github_token_exchange_rejected error={}", response.error());
            throw new BadRequestException(
                    ErrorCode.GITHUB_OAUTH_ERROR,
                    "GitHub rejected the login: "
                            + (response.errorDescription() == null
                                    ? response.error()
                                    : response.errorDescription()));
        }
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ExternalServiceException(
                    ErrorCode.GITHUB_OAUTH_ERROR, "GitHub did not return an access token");
        }
        return response;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
