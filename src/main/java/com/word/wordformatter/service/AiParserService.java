package com.word.wordformatter.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.word.wordformatter.model.FormatConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class AiParserService {

    @Value("${alibaba.dashscope.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将用户自然语言描述解析为 FormatConfig
     */
    public FormatConfig parse(String userInput) throws Exception {

        String systemPrompt = """
        你是一个 Word 文档格式配置助手。
        用户会用自然语言描述他们想要的 Word 文档格式要求。
        你需要将用户的描述解析为一个 JSON 对象，JSON 字段说明如下：

        【页面边距】
        - marginTop: 上边距，数字，单位厘米
        - marginBottom: 下边距，数字，单位厘米
        - marginLeft: 左边距，数字，单位厘米
        - marginRight: 右边距，数字，单位厘米

        【正文字段】
        - bodyChineseFont: 正文中文字体（如：宋体、微软雅黑、黑体、仿宋、楷体）
        - bodyEnglishFont: 正文英文字体（如：Times New Roman、Arial、Calibri、Cambria）
        - bodyFontSize: 正文字号，整数，单位磅
        - bodyLineSpacingRule: 正文行间距规则，只能是：SINGLE / ONE_POINT_FIVE / DOUBLE / EXACT / AT_LEAST
        - bodyLineSpacingValue: 行间距值，整数，单位磅，仅 EXACT 或 AT_LEAST 时需要
        - bodySpaceBefore: 正文段前间距，整数，单位磅
        - bodySpaceAfter: 正文段后间距，整数，单位磅
        - bodyAlignment: 正文对齐方式，只能是：LEFT / CENTER / RIGHT / BOTH
        - bodyFirstLineIndentChars: 正文首行缩进字符数，整数

        【标题1字段】
        - h1ChineseFont / h1EnglishFont / h1FontSize / h1Bold
        - h1LineSpacingRule / h1LineSpacingValue
        - h1SpaceBefore / h1SpaceAfter（单位磅）
        - h1Alignment: LEFT / CENTER / RIGHT / BOTH

        【标题2字段】
        - h2ChineseFont / h2EnglishFont / h2FontSize / h2Bold
        - h2LineSpacingRule / h2LineSpacingValue
        - h2SpaceBefore / h2SpaceAfter（单位磅）
        - h2Alignment: LEFT / CENTER / RIGHT / BOTH

        【标题3字段】
        - h3ChineseFont / h3EnglishFont / h3FontSize / h3Bold
        - h3LineSpacingRule / h3LineSpacingValue
        - h3SpaceBefore / h3SpaceAfter（单位磅）
        - h3Alignment: LEFT / CENTER / RIGHT / BOTH

        【重要规则】
                1. 只输出 JSON，不要输出任何其他文字、解释、markdown代码块
                2. 用户没有提到的字段，一律设为 null
                3. 字体名称保持原始写法，中文字体用中文，英文字体用英文
                4. 字号换算：小四=12、四号=14、小三=15、三号=16、小二=18、二号=22、小一=24、一号=26、初号=42
                5. 行距换算：固定20磅则 rule=EXACT value=20；1.5倍则 rule=ONE_POINT_FIVE
                6. 段前段后"0.5行"换算：0.5 × 字号 × 1.5 ≈ 取整磅数，正文12磅时约等于9磅
                7. "各层标题"或"所有标题"表示 h1、h2、h3 都要同时设置相同的值，不能只设置 h1
                8. 首行缩进"2字符"则 bodyFirstLineIndentChars=2
                9. 边距单位统一用厘米小数，如 2.5
                10. 标题字体未单独说明时，继承正文中文字体和英文字体
                11. "题目"通常指文章大标题，对应 h1
                12. 注意区分"题目居中"和"各层标题左对齐"：题目(h1)用CENTER，各层标题(h2/h3)用LEFT
                13. 段前段后"0.5行"要同时设置所有提到的标题级别的 SpaceBefore 和 SpaceAfter
                14. 返回的 JSON 必须完整包含所有字段，确保括号闭合
                15. 不要返回任何 JSON 之外的内容，不要用 markdown 代码块包裹
                """;

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build();

        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(userInput)
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model("qwen-turbo")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        Generation gen = new Generation();
        GenerationResult result = gen.call(param);

        String jsonStr = result.getOutput().getChoices().get(0).getMessage().getContent();

        // 清理可能的 markdown 代码块标记
        jsonStr = jsonStr.trim();
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.replaceAll("```json", "").replaceAll("```", "").trim();
        }

        return objectMapper.readValue(jsonStr, FormatConfig.class);
    }
}