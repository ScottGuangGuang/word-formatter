package com.word.wordformatter.controller;

import com.word.wordformatter.model.FormatConfig;
import com.word.wordformatter.service.AiParserService;
import com.word.wordformatter.service.WordFormatterService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class WordController {

    private final WordFormatterService wordFormatterService;
    private final AiParserService aiParserService;

    /**
     * 首页
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * AI 解析用户输入，预览解析结果（可选调试用）
     */
    @PostMapping("/parse")
    @ResponseBody
    public FormatConfig parse(@RequestParam("requirement") String requirement) {
        try {
            return aiParserService.parse(requirement);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 上传文件 + 自然语言要求 → AI解析 → 格式化 → 下载
     */
    @PostMapping("/format")
    public void format(
            @RequestParam("file") MultipartFile file,
            @RequestParam("requirement") String requirement,
            HttpServletResponse response) {

        // 1. 校验文件
        if (file.isEmpty()) {
            sendError(response, "请上传文件");
            return;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".docx")) {
            sendError(response, "仅支持 .docx 格式");
            return;
        }

        try {
            // 2. AI 解析自然语言 → FormatConfig
            FormatConfig config = aiParserService.parse(requirement);

            // 3. 构造下载文件名
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf("."));
            String outputFileName = baseName + "_formatted.docx";
            String encodedFileName = URLEncoder.encode(outputFileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            // 4. 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);

            // 5. 格式化并写入响应流
            OutputStream outputStream = response.getOutputStream();
            wordFormatterService.format(file.getInputStream(), outputStream, config);
            outputStream.flush();

        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "处理失败：" + e.getMessage());
        }
    }

    private void sendError(HttpServletResponse response, String message) {
        try {
            response.setContentType("text/html;charset=UTF-8");
            response.setStatus(400);
            response.getWriter().write("<h3 style='color:red'>错误：" + message + "</h3><a href='/'>返回</a>");
        } catch (Exception ignored) {}
    }
}