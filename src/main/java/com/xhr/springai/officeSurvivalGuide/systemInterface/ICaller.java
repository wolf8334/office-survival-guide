package com.xhr.springai.officeSurvivalGuide.systemInterface;

public interface ICaller {

    String call(String expansionPrompt, String requirement);

    String call(String expansionPrompt);
}
