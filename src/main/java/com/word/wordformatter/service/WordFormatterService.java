package com.word.wordformatter.service;

import com.word.wordformatter.model.FormatConfig;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

@Service
public class WordFormatterService {

    public void format(InputStream inputStream, OutputStream outputStream, FormatConfig config) throws Exception {
        // 临时测试：跳过 POI，只做 ZIP 字符串替换
        byte[] inputBytes = inputStream.readAllBytes();
        byte[] result = replaceSpacingInZip(inputBytes, config);
        outputStream.write(result);
        outputStream.flush();
    }

    // ==================== 直接操作 ZIP 替换行距 ====================

    private byte[] replaceSpacingInZip(byte[] docxBytes, FormatConfig config) throws Exception {
        ByteArrayInputStream zipIn = new ByteArrayInputStream(docxBytes);
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();

        try (ZipInputStream zis = new ZipInputStream(zipIn);
             ZipOutputStream zos = new ZipOutputStream(zipOut)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] entryBytes = zis.readAllBytes();

                if (entry.getName().equals("word/document.xml")) {
                    String xml = new String(entryBytes, StandardCharsets.UTF_8);
                    xml = removeExtraSectPr(xml);     // 先删多余分节符
                    xml = replaceAllSpacing(xml, config);  // 再替换行距
                    entryBytes = xml.getBytes(StandardCharsets.UTF_8);
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(newEntry);
                zos.write(entryBytes);
                zos.closeEntry();
            }
        }

        return zipOut.toByteArray();
    }

    /**
     * 删除段落内嵌的 sectPr（分节符），只保留 w:body 末尾的主 sectPr
     * 段落内嵌的 sectPr 会导致多余空白页
     */
    private String removeExtraSectPr(String xml) {
        System.out.println("=== 删除前 sectPr 数量：" + countOccurrences(xml, "<w:sectPr"));

        StringBuffer sb = new StringBuffer();
        Pattern pPrPattern = Pattern.compile("<w:pPr>([\\s\\S]*?)</w:pPr>", Pattern.DOTALL);
        Matcher m = pPrPattern.matcher(xml);

        while (m.find()) {
            String pPrContent = m.group(1);
            if (pPrContent.contains("<w:sectPr")) {
                String cleaned = pPrContent
                        .replaceAll("<w:sectPr[\\s\\S]*?</w:sectPr>", "")
                        .replaceAll("<w:sectPr[^/]*/?>", "");
                m.appendReplacement(sb, Matcher.quoteReplacement("<w:pPr>" + cleaned + "</w:pPr>"));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);

        String result = sb.toString();
        System.out.println("=== 删除后 sectPr 数量：" + countOccurrences(result, "<w:sectPr"));
        return result;
    }

    // ==================== 替换 document.xml 中所有 w:spacing ====================
    private String replaceAllSpacing(String xml, FormatConfig config) {

        String bodySpacing = buildSpacingTag(
                config.getBodyLineSpacingRule(),
                config.getBodyLineSpacingValue(),
                config.getBodySpaceBefore(),
                config.getBodySpaceAfter());

        String h1Spacing = buildSpacingTag(
                config.getH1LineSpacingRule(),
                config.getH1LineSpacingValue(),
                config.getH1SpaceBefore(),
                config.getH1SpaceAfter());

        String h2Spacing = buildSpacingTag(
                config.getH2LineSpacingRule(),
                config.getH2LineSpacingValue(),
                config.getH2SpaceBefore(),
                config.getH2SpaceAfter());

        String h3Spacing = buildSpacingTag(
                config.getH3LineSpacingRule(),
                config.getH3LineSpacingValue(),
                config.getH3SpaceBefore(),
                config.getH3SpaceAfter());

        StringBuffer sb = new StringBuffer();
        Pattern paraPattern = Pattern.compile("<w:p[ >][\\s\\S]*?</w:p>", Pattern.DOTALL);
        Matcher m = paraPattern.matcher(xml);

        while (m.find()) {
            String para = m.group();

            // 跳过含分节符的段落
            if (para.contains("<w:sectPr")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(para));
                continue;
            }

            // 跳过表格内段落（含 w:tc 标记的不会出现在顶层，但双重保护）
            // 只处理有明确样式或有文字内容的段落
            boolean hasText = para.contains("<w:t>") || para.contains("<w:t ");
            boolean isHeading1 = para.contains("<w:pStyle w:val=\"1\"") ||
                    para.contains("<w:pStyle w:val=\"Heading1\"");
            boolean isHeading2 = para.contains("<w:pStyle w:val=\"2\"") ||
                    para.contains("<w:pStyle w:val=\"Heading2\"");
            boolean isHeading3 = para.contains("<w:pStyle w:val=\"3\"") ||
                    para.contains("<w:pStyle w:val=\"Heading3\"");

            // 没有文字内容的空段落跳过，避免撑开页面
            if (!hasText && !isHeading1 && !isHeading2 && !isHeading3) {
                m.appendReplacement(sb, Matcher.quoteReplacement(para));
                continue;
            }

            String spacing = bodySpacing;
            if (isHeading1) {
                spacing = h1Spacing;
            } else if (isHeading2) {
                spacing = h2Spacing;
            } else if (isHeading3) {
                spacing = h3Spacing;
            }

            if (spacing != null) {
                para = replacePPrSpacing(para, spacing);
            }

            m.appendReplacement(sb, Matcher.quoteReplacement(para));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    // 只替换段落属性 pPr 内的 spacing
    private String replacePPrSpacing(String para, String newSpacingTag) {
        // 匹配 <w:pPr>...</w:pPr> 块
        Pattern pPrPattern = Pattern.compile("<w:pPr>([\\s\\S]*?)</w:pPr>");
        Matcher pPrMatcher = pPrPattern.matcher(para);

        if (pPrMatcher.find()) {
            String pPrContent = pPrMatcher.group(1);

            // 替换或插入 spacing
            String newPPrContent;
            if (pPrContent.contains("<w:spacing")) {
                newPPrContent = pPrContent.replaceAll("<w:spacing\\b[^/]*/?>", newSpacingTag);
            } else {
                newPPrContent = pPrContent + newSpacingTag;
            }

            return para.replace(pPrMatcher.group(0), "<w:pPr>" + newPPrContent + "</w:pPr>");

        } else if (para.contains("<w:pPr/>")) {
            // 自闭合的 pPr
            return para.replace("<w:pPr/>", "<w:pPr>" + newSpacingTag + "</w:pPr>");
        } else {
            // 没有 pPr，在 <w:p...> 后插入
            return para.replaceFirst("(<w:p[^>]*>)", "$1<w:pPr>" + newSpacingTag + "</w:pPr>");
        }
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // ==================== 替换单个段落的 w:spacing ====================



    // ==================== 构建 spacing 标签字符串 ====================

    private String buildSpacingTag(String rule, Integer value, Integer before, Integer after) {
        if (rule == null && before == null && after == null) return null;

        String lineVal, lineRuleVal;
        switch (rule != null ? rule.toUpperCase() : "") {
            case "SINGLE"         -> { lineVal = "240"; lineRuleVal = "auto"; }
            case "ONE_POINT_FIVE" -> { lineVal = "276"; lineRuleVal = "auto"; }
            case "DOUBLE"         -> { lineVal = "480"; lineRuleVal = "auto"; }
            case "EXACT"          -> {
                lineVal = value != null ? String.valueOf(value * 20) : "240";
                lineRuleVal = "exact";
            }
            case "AT_LEAST"       -> {
                lineVal = value != null ? String.valueOf(value * 20) : "240";
                lineRuleVal = "atLeast";
            }
            default -> { lineVal = null; lineRuleVal = null; }
        }

        StringBuilder attrs = new StringBuilder();
        if (before      != null) attrs.append(" w:before=\"").append(before * 20).append("\"");
        if (after       != null) attrs.append(" w:after=\"").append(after * 20).append("\"");
        if (lineVal     != null) attrs.append(" w:line=\"").append(lineVal).append("\"");
        if (lineRuleVal != null) attrs.append(" w:lineRule=\"").append(lineRuleVal).append("\"");

        if (attrs.length() == 0) return null;
        return "<w:spacing" + attrs + "/>";
    }

    // ==================== 只用 POI 处理字体、对齐、缩进 ====================

    private void applyBodyFontAlign(XWPFParagraph paragraph, FormatConfig config) {
        applyAlignment(paragraph, config.getBodyAlignment());
        applyFirstLineIndent(paragraph, config.getBodyFirstLineIndentChars(), config.getBodyFontSize());
        for (XWPFRun run : paragraph.getRuns()) {
            applyFont(run, config.getBodyChineseFont(), config.getBodyEnglishFont(),
                    config.getBodyFontSize(), null);
        }
    }

    private void applyHeadingFontAlign(XWPFParagraph paragraph, FormatConfig config, int level) {
        String chineseFont, englishFont, alignment;
        Integer fontSize;
        Boolean bold;

        switch (level) {
            case 1 -> {
                chineseFont = config.getH1ChineseFont();
                englishFont = config.getH1EnglishFont();
                fontSize    = config.getH1FontSize();
                bold        = config.getH1Bold();
                alignment   = config.getH1Alignment();
            }
            case 2 -> {
                chineseFont = config.getH2ChineseFont();
                englishFont = config.getH2EnglishFont();
                fontSize    = config.getH2FontSize();
                bold        = config.getH2Bold();
                alignment   = config.getH2Alignment();
            }
            default -> {
                chineseFont = config.getH3ChineseFont();
                englishFont = config.getH3EnglishFont();
                fontSize    = config.getH3FontSize();
                bold        = config.getH3Bold();
                alignment   = config.getH3Alignment();
            }
        }

        applyAlignment(paragraph, alignment);
        for (XWPFRun run : paragraph.getRuns()) {
            applyFont(run, chineseFont, englishFont, fontSize, bold);
        }
    }

    // ==================== 字体 ====================

    private void applyFont(XWPFRun run, String chineseFont, String englishFont,
                           Integer fontSize, Boolean bold) {
        if (chineseFont != null && !chineseFont.isBlank()) {
            run.setFontFamily(chineseFont, XWPFRun.FontCharRange.eastAsia);
            run.setFontFamily(chineseFont, XWPFRun.FontCharRange.cs);
        }
        if (englishFont != null && !englishFont.isBlank()) {
            run.setFontFamily(englishFont, XWPFRun.FontCharRange.ascii);
            run.setFontFamily(englishFont, XWPFRun.FontCharRange.hAnsi);
        }
        if (fontSize != null && fontSize > 0) {
            run.setFontSize(fontSize);
        }
        if (bold != null) {
            run.setBold(bold);
        }
    }

    // ==================== 对齐 ====================

    private void applyAlignment(XWPFParagraph paragraph, String alignment) {
        if (alignment == null || alignment.isBlank()) return;
        switch (alignment.toUpperCase()) {
            case "LEFT"   -> paragraph.setAlignment(ParagraphAlignment.LEFT);
            case "CENTER" -> paragraph.setAlignment(ParagraphAlignment.CENTER);
            case "RIGHT"  -> paragraph.setAlignment(ParagraphAlignment.RIGHT);
            case "BOTH"   -> paragraph.setAlignment(ParagraphAlignment.BOTH);
        }
    }

    // ==================== 首行缩进 ====================

    private void applyFirstLineIndent(XWPFParagraph paragraph, Integer chars, Integer fontSize) {
        if (chars == null || chars <= 0) return;
        int pt = (fontSize != null && fontSize > 0) ? fontSize : 12;
        int twips = chars * pt * 20;
        CTPPr pPr = getOrCreatePPr(paragraph);
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setFirstLine(BigInteger.valueOf(twips));
    }

    // ==================== 页面边距 ====================

    private void applyPageMargin(XWPFDocument doc, FormatConfig config) {
        if (config.getMarginTop() == null && config.getMarginBottom() == null
                && config.getMarginLeft() == null && config.getMarginRight() == null) return;

        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();

        if (config.getMarginTop()    != null) pageMar.setTop(BigInteger.valueOf(Math.round(config.getMarginTop() * 567)));
        if (config.getMarginBottom() != null) pageMar.setBottom(BigInteger.valueOf(Math.round(config.getMarginBottom() * 567)));
        if (config.getMarginLeft()   != null) pageMar.setLeft(BigInteger.valueOf(Math.round(config.getMarginLeft() * 567)));
        if (config.getMarginRight()  != null) pageMar.setRight(BigInteger.valueOf(Math.round(config.getMarginRight() * 567)));
    }

    // ==================== 工具 ====================

    private boolean isHeading(String style, int level) {
        if (style == null) return false;
        return style.equals(String.valueOf(level))
                || style.equalsIgnoreCase("Heading" + level)
                || style.equalsIgnoreCase("heading " + level)
                || style.equalsIgnoreCase("标题" + level);
    }

    private CTPPr getOrCreatePPr(XWPFParagraph paragraph) {
        CTP ctp = paragraph.getCTP();
        return ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
    }
}