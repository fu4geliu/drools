package com.example.drools.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读取 Drools 规则文件内容，供 AI 或其他模块使用。
 */
@Service
public class DroolsRuleReaderService {

    private static final String RULES_LOCATION = "classpath:rules/*.drl";

    /**
     * 获取所有 .drl 规则文件的原始内容，按文件名排序。
     */
    public String getAllRulesContent() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(RULES_LOCATION);

        List<Resource> list = new ArrayList<>(List.of(resources));
        list.sort(Comparator.comparing(r -> {
            try {
                return r.getFilename() != null ? r.getFilename() : "";
            } catch (Exception e) {
                return "";
            }
        }));

        StringBuilder sb = new StringBuilder();
        for (Resource res : list) {
            String filename = res.getFilename();
            sb.append("=== 规则文件: ").append(filename).append(" ===\n\n");
            sb.append(res.getContentAsString(StandardCharsets.UTF_8)).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 获取规则内容，若读取失败返回空字符串。
     */
    public String getAllRulesContentSafe() {
        try {
            return getAllRulesContent();
        } catch (IOException e) {
            return "";
        }
    }
}
