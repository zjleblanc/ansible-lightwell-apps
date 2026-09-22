package com.lightwell.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the repo, deployed commit SHA, PR number, and environment to
 * every template.
 */
@ControllerAdvice(annotations = Controller.class)
public class TemplateContextAdvice {

    @ModelAttribute("githubRepo")
    public String githubRepo() {
        return System.getenv().getOrDefault("GITHUB_REPO", "");
    }

    @ModelAttribute("appGitSha")
    public String appGitSha() {
        return System.getenv().getOrDefault("APP_GIT_SHA", "");
    }

    @ModelAttribute("githubPrNumber")
    public String githubPrNumber() {
        return System.getenv().getOrDefault("GITHUB_PR_NUMBER", "");
    }

    @ModelAttribute("appEnv")
    public String appEnv() {
        return System.getenv().getOrDefault("APP_ENVIRONMENT", "prod");
    }
}
