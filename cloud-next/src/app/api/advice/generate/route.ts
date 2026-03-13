import { NextResponse } from "next/server";
import { ok, parseJsonBody } from "../../../../lib/http";

type AdviceBody = {
  recoveryScore?: number;
};

export async function POST(req: Request) {
  const body = parseJsonBody<AdviceBody>(await req.json().catch(() => ({})));
  const score = Number(body.recoveryScore ?? 0);

  const data =
    score >= 80
      ? [
          { type: "EXERCISE", title: "高强度训练", description: "状态较好，可进行高强度训练。" },
          { type: "FOCUS", title: "专注时段", description: "建议上午安排高认知任务。" },
        ]
      : score >= 60
      ? [
          { type: "EXERCISE", title: "中等强度运动", description: "建议中等强度有氧运动。" },
          { type: "SLEEP", title: "保持作息", description: "建议今晚保持稳定入睡时间。" },
        ]
      : [
          { type: "SLEEP", title: "优先补觉", description: "建议提前入睡并减少刺激活动。" },
          { type: "STRESS", title: "减压放松", description: "建议进行呼吸放松练习。" },
        ];

  return NextResponse.json(ok("ok", data));
}
