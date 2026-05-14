package com.word.wordformatter.model;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)  // 加这一行
public class FormatConfig {

    // ==================== 页面设置 ====================
    /** 上边距（厘米） */
    private Double marginTop;
    /** 下边距（厘米） */
    private Double marginBottom;
    /** 左边距（厘米） */
    private Double marginLeft;
    /** 右边距（厘米） */
    private Double marginRight;

    // ==================== 正文 ====================
    private String bodyChineseFont;
    private String bodyEnglishFont;
    private Integer bodyFontSize;
    private String bodyLineSpacingRule;
    private Integer bodyLineSpacingValue;
    private Integer bodySpaceBefore;
    private Integer bodySpaceAfter;
    /** 正文对齐方式：LEFT / CENTER / RIGHT / BOTH */
    private String bodyAlignment;
    /** 正文首行缩进（字符数） */
    private Integer bodyFirstLineIndentChars;

    // ==================== 标题1 ====================
    private String h1ChineseFont;
    private String h1EnglishFont;
    private Integer h1FontSize;
    private Boolean h1Bold;
    private String h1LineSpacingRule;
    private Integer h1LineSpacingValue;
    private Integer h1SpaceBefore;
    private Integer h1SpaceAfter;
    /** 标题1对齐方式：LEFT / CENTER / RIGHT / BOTH */
    private String h1Alignment;

    // ==================== 标题2 ====================
    private String h2ChineseFont;
    private String h2EnglishFont;
    private Integer h2FontSize;
    private Boolean h2Bold;
    private String h2LineSpacingRule;
    private Integer h2LineSpacingValue;
    private Integer h2SpaceBefore;
    private Integer h2SpaceAfter;
    private String h2Alignment;

    // ==================== 标题3 ====================
    private String h3ChineseFont;
    private String h3EnglishFont;
    private Integer h3FontSize;
    private Boolean h3Bold;
    private String h3LineSpacingRule;
    private Integer h3LineSpacingValue;
    private Integer h3SpaceBefore;
    private Integer h3SpaceAfter;
    private String h3Alignment;
}