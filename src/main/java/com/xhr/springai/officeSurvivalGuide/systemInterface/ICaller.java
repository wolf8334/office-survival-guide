package com.xhr.springai.officeSurvivalGuide.systemInterface;

public interface ICaller {

    public String call(String expansionPrompt,String requirement);

    public String call(String expansionPrompt);
}
