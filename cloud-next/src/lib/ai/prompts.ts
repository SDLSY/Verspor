export type SpeechPromptProfile =
  | "doctor_summary"
  | "sleep_coach"
  | "calm_assistant";

export type ImagePromptProfile =
  | "medical_wellness_product"
  | "medical_education_illustration"
  | "clinical_ui_cover";

export type VideoPromptProfile =
  | "sleep_guidance"
  | "recovery_demo"
  | "calm_product_story";

export function buildSpeechTranscriptionPrompt(hint: string): string {
  const baseRules = [
    "这是睡眠、恢复、压力、呼吸和健康问询场景的中文语音转写。",
    "请优先保留症状、时长、频率、时间段、诱因、缓解因素、部位、严重程度等关键信息。",
    "请正确识别常见健康相关词汇，如血氧、心率、HRV、失眠、胸闷、头晕、疲劳、焦虑、呼吸困难、夜间醒来、睡前、晨起、运动后。",
    "只输出转写文本，不要总结，不要改写，不要补充未说出的信息。",
  ];

  if (hint.trim()) {
    baseRules.push(`补充上下文提示：${hint.trim()}`);
  }

  return baseRules.join(" ");
}

export function buildSpeechExampleSet(): Record<SpeechPromptProfile, string[]> {
  return {
    doctor_summary: [
      "根据最近一周睡眠和恢复情况，你当前最需要先稳定晚间入睡节律，再观察晨起疲劳是否下降。",
      "从问诊结果看，当前更像压力和睡眠双重负荷，建议先做一个短时可执行的恢复动作，不要继续加训练量。",
      "这次结构化问诊没有发现必须立即急诊的红旗，但如果胸痛、呼吸困难或意识异常加重，仍应优先线下就医。"
    ],
    sleep_coach: [
      "今晚先把灯光和屏幕刺激降下来，给自己一个更容易入睡的环境。",
      "如果脑子停不下来，先做几分钟慢呼气，把节律放慢，再回到床上。",
      "明天先固定起床时间，不要因为昨晚睡不好就一直补觉。"
    ],
    calm_assistant: [
      "我已经听到了，你可以先深呼吸，我们一步一步来。",
      "先别着急，我会先帮你整理主要不适和触发因素。",
      "我们先把信息讲清楚，再决定下一步该怎么做。"
    ]
  };
}

export function buildSpeechRewritePrompt(
  text: string,
  profile: SpeechPromptProfile
): { systemPrompt: string; userPrompt: string } {
  const profileRule = (() => {
    switch (profile) {
      case "doctor_summary":
        return "目标是把医学/健康说明改写成冷静、可信、易听懂的中文播报稿，适合 20-45 秒口播。";
      case "sleep_coach":
        return "目标是把说明改写成温和、低刺激、适合睡前播放的中文引导稿。";
      case "calm_assistant":
        return "目标是把说明改写成简洁、稳定、像健康助手播报的中文语音稿。";
    }
  })();

  const systemPrompt = [
    "你是语音播报稿编辑器。",
    profileRule,
    "请把输入文本改写成适合 TTS 朗读的简体中文。",
    "句子要短，逻辑清楚，避免书面括号、项目符号、Markdown 和链接。",
    "保留事实，不夸张，不制造诊断结论。",
    "输出纯文本，不要解释。",
  ].join(" ");

  const userPrompt = `请把下面内容改写成适合语音播报的文本：\n${text.trim()}`;

  return { systemPrompt, userPrompt };
}

export function buildImageGenerationPrompt(
  prompt: string,
  profile: ImagePromptProfile
): string {
  const styleBlock = (() => {
    switch (profile) {
      case "medical_wellness_product":
        return [
          "Create a polished medical wellness product visual.",
          "High-trust healthcare aesthetic, clean composition, soft natural light, premium but restrained, no sci-fi clutter.",
          "Show calm, credible, consumer-grade health technology.",
          "Avoid gore, surgery, needles, explicit diagnosis text, horror, and exaggerated biotech visuals.",
        ].join(" ");
      case "medical_education_illustration":
        return [
          "Create a clear educational medical illustration.",
          "Readable, structured, calm, accurate visual hierarchy, suitable for a health app explainer.",
          "Use simplified forms, clear focal point, and neutral palette.",
          "Avoid cartoonish distortion, gore, and sensational visuals.",
        ].join(" ");
      case "clinical_ui_cover":
        return [
          "Create a clean hero image suitable for a clinical or health dashboard cover.",
          "Minimal composition, strong whitespace, trustworthy visual tone, modern healthcare brand style.",
          "Avoid busy backgrounds, decorative clutter, and fantasy elements.",
        ].join(" ");
    }
  })();

  return [
    styleBlock,
    "Subject request:",
    prompt.trim(),
  ].join("\n\n");
}

export function buildImageExampleSet(): Record<ImagePromptProfile, string[]> {
  return {
    medical_wellness_product: [
      "生成一张适合医生解释睡眠恢复的健康产品封面图",
      "生成一张展示智能睡眠指环与床头环境的高信任感产品图",
      "生成一张恢复管理场景下的 calm medical wellness hero image"
    ],
    medical_education_illustration: [
      "生成一张睡眠分期科普插图",
      "生成一张解释压力、睡眠和恢复关系的医学教育插图",
      "生成一张医检指标异常后如何做居家恢复的说明图"
    ],
    clinical_ui_cover: [
      "生成一张适合作为 App 健康封面的干净临床风图",
      "生成一张适合作为医生问诊页顶部封面的医疗科技背景图",
      "生成一张适合恢复管理 dashboard 的简洁临床风封面"
    ]
  };
}

export function buildVideoGenerationPrompt(
  prompt: string,
  profile: VideoPromptProfile,
  durationSec: number
): string {
  const template = (() => {
    switch (profile) {
      case "sleep_guidance":
        return [
          "Generate a calm, low-stimulation wellness guidance video.",
          "Stable camera, gentle motion, soft lighting, quiet evening atmosphere, no jump cuts.",
          "Suitable for sleep preparation or recovery guidance.",
          "Avoid medical panic, emergency scenes, gore, and intense motion.",
        ].join(" ");
      case "recovery_demo":
        return [
          "Generate a practical recovery exercise demonstration video.",
          "Single clear action focus, human-readable pacing, stable framing, suitable for app guidance.",
          "Show controlled movement, clean background, no fitness hype style.",
          "Avoid unsafe movement, pain reactions, or exaggerated athletic performance.",
        ].join(" ");
      case "calm_product_story":
        return [
          "Generate a premium but restrained product-story video for a health technology product.",
          "Slow cinematic motion, clean environment, strong clarity of subject, trustworthy medical wellness tone.",
          "Avoid flashy advertising tropes, cyberpunk effects, and cluttered scene transitions.",
        ].join(" ");
    }
  })();

  return [
    template,
    `Target duration: about ${durationSec} seconds.`,
    "Scene request:",
    prompt.trim(),
  ].join("\n\n");
}

export function buildVideoExampleSet(): Record<VideoPromptProfile, string[]> {
  return {
    sleep_guidance: [
      "生成一个睡前放松引导短片",
      "生成一个低刺激、稳定镜头的睡前呼吸引导视频",
      "生成一个帮助用户在夜间放慢节律的 calm sleep guidance clip"
    ],
    recovery_demo: [
      "生成一个低负担恢复动作演示短片",
      "生成一个适合疲劳恢复日的轻量拉伸示范视频",
      "生成一个呼吸加轻活动度恢复的康复动作演示短片"
    ],
    calm_product_story: [
      "生成一个 calm product story 风格的睡眠产品短片",
      "生成一个展示智能睡眠指环如何陪伴用户晚间恢复的短片",
      "生成一个医疗健康科技产品的平静叙事短视频"
    ]
  };
}
