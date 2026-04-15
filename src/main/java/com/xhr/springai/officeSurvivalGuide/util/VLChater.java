package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class VLChater implements ICaller {

    @Qualifier("vlClient")
    private final ChatClient vl;

    @Override
    public String call(String requirement, String base64Image) {
        // 1. 将 Base64 字符串解码为字节数组
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        // 2. 使用 ByteArrayResource 包装
        Resource resource = new ByteArrayResource(imageBytes);
        return vl.prompt().system("请对这张图片进行 OCR 识别").user(u ->
                u.text(requirement).media(MediaType.IMAGE_JPEG, resource)).call().content();
    }

    @Override
    public String call(String expansionPrompt) {
        return "";
    }
}
