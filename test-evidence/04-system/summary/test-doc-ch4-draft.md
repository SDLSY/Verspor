# 4 系统测试

## 4.1 模型性能测试

### 4.1.1 睡眠分析链路性能测试
睡眠分析链路在提交版复测中以端云协同方式稳定完成多次推理，平均耗时 5849.44 ms，链路结果可被页面与记录消费，结论为通过。证据文件路径：`D:/newstart/test-evidence/04-system/4.1.1-sleep-analysis/raw-metrics.csv`

### 4.1.2 建议生成模型（SRM_V2）性能测试
SRM_V2 在多源证据整合、安全门控与表达层生成场景下表现稳定，平均耗时 6107.93 ms，当前证明的是混合建议引擎可用性，结论为通过。证据文件路径：`D:/newstart/test-evidence/04-system/4.1.2-srmv2/raw-metrics.csv`

### 4.1.3 系统级联动性能测试
系统级联动测试表明今日页、医生页、报告理解、机器人播报、干预回写与建议消费已在真实业务闭环中联动完成，平均耗时 6591.19 ms，结论为通过。证据文件路径：`D:/newstart/test-evidence/04-system/4.1.3-system-integration/raw-metrics.csv`
