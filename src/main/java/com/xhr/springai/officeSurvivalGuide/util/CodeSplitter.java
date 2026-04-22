package com.xhr.springai.officeSurvivalGuide.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CodeSplitter {

    public static void updateRouteFile(String routeFilePath,
                                       String importLine,
                                       String spreadLine) throws IOException {
        Path path = Paths.get(routeFilePath);
        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));

        // 1. 删掉已有的同名 import 和 spread，避免重复
        lines.removeIf(line -> line.trim().equals(importLine.trim())
                || line.trim().equals(spreadLine.trim()));

        // 2. 找第一个 import，在它后面插入
        int importIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("import ")) {
                importIndex = i + 1;
                break;
            }
        }
        if (importIndex != -1) {
            lines.add(importIndex, importLine);
        }

        // 3. 找 spread 插入位置，比如找到 ...smartParking 后面插入
        // 这里用最后一个 ...xxx, 的位置，在它后面加
        int spreadIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().matches("^\\.\\.\\.\\w+,.*")) {
                spreadIndex = i + 1;
                break;
            }
        }
        if (spreadIndex != -1) {
            lines.add(spreadIndex, spreadLine);
        }

        Files.write(path, lines, StandardCharsets.UTF_8);
        log.info("路由文件已更新");
    }

    public Map<String, String> split(String rawCode) {
        Map<String, String> files = new LinkedHashMap<>();
        String[] parts = rawCode.split("------");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // 第一行是文件名，剩下的是内容
            int lineEnd = trimmed.indexOf("\n");
            if (lineEnd == -1) continue;
            String filename = trimmed.substring(0, lineEnd).trim();
            String content = trimmed.substring(lineEnd).trim();
            if (!filename.isEmpty() && !content.isEmpty()) {
                files.put(filename, content);
            }
        }
        return files;
    }

    public void writeFiles(Map<String, String> files, String baseJavaPath, String baseResourcePath, String baseVuePath, String uriPath, String busiName, String busiChnName) {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();
            String outputPath;

            try {
                if (filename.endsWith(".xml")) {
                    outputPath = baseResourcePath + "\\" + filename;
                } else if (filename.endsWith(".vue")) {
                    // 每个模块生成一个单独的文件夹
                    String vuePath = filename.replace(".vue" , "");
                    Files.createDirectories(Paths.get(baseVuePath + "\\pages\\" + File.separator + vuePath));
                    outputPath = baseVuePath + "\\pages\\" + vuePath + File.separator + filename;

                    content = content.replaceAll("from '[^']*Api'","from '@/api" + uriPath + "/" + busiName + "Api'");
                } else if (filename.endsWith("Api.js")) {
                    // 每个模块生成一个单独的文件夹
                    String apiPath = filename.replace("Api.js" , "");
                    Files.createDirectories(Paths.get(baseVuePath + "\\api" + File.separator + uriPath.replace("/",File.separator)));
                    outputPath = baseVuePath + "\\api" +  uriPath.replace("/",File.separator) + File.separator + filename;
                } else if (filename.endsWith("Route.js")) {
                    // 每个模块生成一个单独的文件夹
                    String routePath = filename.replace("Route.js" , "");
                    Files.createDirectories(Paths.get(baseVuePath + "\\router\\modules\\" + File.separator + routePath));
                    outputPath = baseVuePath + "\\router\\modules\\" + routePath + File.separator + filename;
                    writeRouteFile(outputPath, uriPath, busiName, busiChnName);

                    //更新route.js
                    String routeConfigPath = baseVuePath + "\\router\\routes.js";
                    String importStr = "import %s from \"./modules/%s/%s\";".formatted(routePath, routePath, filename).replace(".js" , "");
                    String frameInStr = "\t%s,".formatted(routePath);
                    updateRouteFile(routeConfigPath, importStr, frameInStr);
                } else if (filename.startsWith("I") && filename.endsWith("Service.java")) {
                    // service 接口
                    outputPath = baseJavaPath + "\\service\\" + filename;
                } else if (filename.endsWith("ServiceImpl.java")) {
                    outputPath = baseJavaPath + "\\service\\impl\\" + filename;
                } else if (filename.endsWith("Controller.java")) {
                    outputPath = baseJavaPath + "\\controller\\" + filename;
                } else if (filename.endsWith("Dao.java")) {
                    outputPath = baseJavaPath + "\\dao\\" + filename;
                } else if (filename.endsWith("Mapper.java")) {
                    outputPath = baseJavaPath + "\\mapper\\" + filename;
                } else {
                    log.info("未识别的文件 {}" , filename);
                    outputPath = baseJavaPath + "\\" + filename;
                }

                if (!filename.endsWith("Route.js")) {
                    Path path = Paths.get(outputPath);

                    Files.createDirectories(path.getParent());
                    Files.deleteIfExists(path);
                    Files.writeString(path, content, StandardCharsets.UTF_8);
                }
                log.info("写入: " + outputPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeFiles(String files, String baseJavaPath, String baseResourcePath, String baseVuePath, String uriPath, String busiName, String busiChnName) {
        writeFiles(split(files), baseJavaPath, baseResourcePath, baseVuePath, uriPath, busiName, busiChnName);
    }

    private void writeRouteFile(String filename, String uriPath, String busiName, String busiChnName) throws IOException {
        String capitalized = capitalize(busiName);
        String content = """
                import BasicLayout from '@/layouts';
                
                const meta = {
                    auth: true
                };
                
                const pre = '%s-';
                
                let _pre = '';
                if (process.env.NODE_ENV !== "development") {
                    _pre = '%s';
                }
                
                export default {
                    path: _pre + '/%s',
                    name: '%s',
                    redirect: {
                        name: `${pre}%s`
                    },
                    meta,
                    component: BasicLayout,
                    children: [
                        {
                            path: '%s',
                            name: `${pre}%s`,
                            meta: {
                                ...meta,
                                title: '%s',
                                closable: false
                            },
                            component: () => import('@/pages/%s/%s')
                        }
                    ]
                };
                """.formatted(busiName, uriPath, capitalized, capitalized, capitalized, capitalized, capitalized, busiChnName, capitalized, capitalized);
        Files.delete(Paths.get(filename));
        Files.writeString(Paths.get(filename), content, StandardCharsets.UTF_8);
    }

    public String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
