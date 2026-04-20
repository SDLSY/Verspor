> 文档状态：历史材料
>
> 本文档形成于早期单模块阶段，当前不作为代码事实来源。
> 设备协议与采集策略请优先参考 `docs/20_专题系统说明/戒指通讯协议使用说明与阶段性总结.md`、
> `docs/10_项目事实与架构/06_技术文档基线_2026-03-05.md` 和 `docs/10_项目事实与架构/MODULE_MAP.md`。
# Clevering智能戒指蓝牙配置说明

> 更新时间：2026-01-23  
> 设备型号：Clevering (Hi90B)  
> 设备名称：Clevering_EB6E

---

## 🔧 关键配置

### UUID配置

根据实际设备测试，Clevering智能戒指使用**自定义UUID**：

```kotlin
// 主服务UUID
SERVICE_UUID = 0xFFFA (65530)
完整UUID: 0000FFFA-0000-1000-8000-00805F9B34FB

// 通知特性UUID（接收数据）
NOTIFY_CHARACTERISTIC_UUID = 0xFFFC (65532)
完整UUID: 0000FFFC-0000-1000-8000-00805F9B34FB

// 写入特性UUID（发送命令）
WRITE_CHARACTERISTIC_UUID = 0xFFFB (65531)
完整UUID: 0000FFFB-0000-1000-8000-00805F9B34FB

// CCC描述符UUID（标准）
CCC_DESCRIPTOR_UUID = 00002902-0000-1000-8000-00805f9b34fb
```

### 设备扫描

**设备名称模式**：
- `Clevering_XXXX` （例如：Clevering_EB6E）
- 也支持 `Hi90B` 开头的设备

---

## 📡 连接流程

### 1. 扫描设备
```kotlin
扫描过滤器：
- 设备名称包含 "Clevering" 或 "Hi90B"
- 自动显示在设备列表中
```

### 2. 建立GATT连接
```kotlin
device.connectGatt(context, autoConnect=true, callback)
```

### 3. 服务发现
```kotlin
连接成功后 → discoverServices()
找到服务 0xFFFA
```

### 4. 启用通知
```kotlin
获取特性 0xFFFC
启用通知 → setCharacteristicNotification()
写入CCC描述符 → writeDescriptor()
```

### 5. 接收数据
```kotlin
通过 onCharacteristicChanged() 回调接收数据
```

---

## 📦 数据包格式

### 数据包结构
```
[0-1]  包头: 0xAA 0x55
[2-3]  数据长度
[4]    命令字/应答键
[5]    测量项目类型
[6-13] 保留/数据
[14-19] 时间戳 (年月日时分秒)
[20-]  测量数据
[最后]  校验和 (XOR)
```

### 测量项目类型
| 类型值 | 含义 | 数据位置 |
|-------|------|---------|
| 0x01 | PPG波形 | [20-] |
| 0x02 | 心率 | [20-21] |
| 0x03 | 心率+血氧 | [20-23] |
| 0x05 | 体温 | [20-21] |
| 0x80 | 运动传感器 | [20-] |
| 0x81 | 步数 | [20-23] |

---

## 🔍 数据解析示例

### 心率+血氧数据 (type=0x03)
```kotlin
val heartRate = ((data[20] & 0xFF) * 256) + (data[21] & 0xFF)
val bloodOxygen = ((data[22] & 0xFF) * 256) + (data[23] & 0xFF)

// 示例输出：
// 心率: 72 bpm
// 血氧: 97%
```

### 体温数据 (type=0x05)
```kotlin
val tempRaw = ((data[20] & 0xFF) * 256) + (data[21] & 0xFF)
val temperature = tempRaw * 0.1f

// 示例输出：
// 体温: 36.5°C
```

### PPG波形数据 (type=0x01)
```kotlin
val channel = data[20]  // 采样通道
val sampleRate = ((data[21] & 0xFF) * 256) + (data[22] & 0xFF)

// 从字节23开始，每6字节为一组PPG数据
// 红外通道(3字节) + 红灯通道(3字节)
```

---

## ⚙️ 设备命令

### 查询电量
```kotlin
val cmd = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x00, 0x02, 0x22)
// 加上校验和后发送
```

### 设置时间
```kotlin
val cmd = byteArrayOf(
    0xAA.toByte(), 0x55.toByte(), 0x00, 0x09, 0x21,
    year, month, day, week, hour, minute, second
)
// 加上校验和后发送
```

### 设置心率测量参数 (0x30)
```kotlin
val cmd = byteArrayOf(
    0xAA.toByte(), 0x55.toByte(), 0x00, 0x16, 0x30,
    0x00, 0x03,  // 通道ID=3 (心率)
    0x00, 0x03,  // 工作模式=3
    0x00, 0x05,  // 采样频率=5Hz
    0x00, 0x00, 0x00, 0x00,  // 起始时间
    0x05, 0xA0.toByte(),     // 持续时间
    0x00, 0x00,
    0xEA.toByte(), 0x60,     // 间隔时间
    0x00, 0x00,
    0x75, 0x30
)
// 加上校验和后发送
```

### 启动即时采集 (0x34)
```kotlin
val cmd = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x00, 0x03, 0x34, 0x01)
// 加上校验和后发送
```

---

## 🔧 已修改的代码

### BleManager.kt
1. ✅ 更新服务UUID为 0xFFFA
2. ✅ 更新特性UUID为 0xFFFC (notify) 和 0xFFFB (write)
3. ✅ 添加Clevering数据包解析
4. ✅ 添加描述符写入回调

### 扫描过滤器
```kotlin
// 现在会识别这两种设备名：
if (deviceName.contains("Clevering", ignoreCase = true) || 
    deviceName.contains("Hi90B", ignoreCase = true)) {
    scanCallback?.onDeviceFound(device, rssi)
}
```

---

## 🧪 测试步骤

### 1. 编译并安装应用
```bash
重新编译项目
安装到手机
```

### 2. 准备设备
- 确保Clevering戒指已充电
- 确保戒指已开机
- 将戒指靠近手机（<5米）

### 3. 扫描并连接
1. 打开应用 → "设备"页面
2. 点击"扫描设备"
3. 等待10秒，应该能看到 "Clevering_EB6E"
4. 点击"连接"按钮
5. 观察连接状态

### 4. 查看日志
打开Logcat，过滤"BleManager"：
```bash
adb logcat | grep "BleManager"
```

**预期日志**：
```
I/BleManager: Found device: Clevering_EB6E (XX:XX:XX:XX:XX:XX), RSSI: -45
I/BleManager: Connected to GATT server
I/BleManager: Services discovered
I/BleManager: CCC Descriptor written successfully
D/BleManager: Received data type: 0x32
D/BleManager: 心率: 72, 血氧: 97
```

---

## ❓ 连接问题排查

### 如果还是连接不上

**检查1：设备是否已配对**
- 进入手机蓝牙设置
- 查看是否已经配对过
- 如果配对了，先取消配对再试

**检查2：权限是否授予**
```
需要的权限：
- BLUETOOTH_SCAN
- BLUETOOTH_CONNECT  
- ACCESS_FINE_LOCATION
```

**检查3：autoConnect参数**
当前使用 `autoConnect = true`，这是推荐设置。
如果不行，可以尝试 `false`。

**检查4：设备是否被其他应用占用**
- 关闭其他蓝牙应用
- 重启手机蓝牙
- 重启应用

**检查5：距离和信号**
- RSSI值应该 > -70 (越接近0越好)
- 移除障碍物
- 靠近测试

---

## 📝 下一步优化

### 添加命令发送功能
创建一个工具类，发送各种命令：
```kotlin
class CleveringCommandSender(private val bleManager: BleManager) {
    fun queryBattery()           // 查询电量
    fun setTime()                // 同步时间
    fun startHeartRateMeasure()  // 开始心率测量
    fun startTemperatureMeasure() // 开始体温测量
}
```

### 完善数据解析
- [ ] PPG波形数据解析
- [ ] 运动传感器数据
- [ ] 步数统计
- [ ] 历史数据读取

---

## 🎯 重点提示

**核心变化**：
- ❌ 不再使用标准蓝牙心率服务 (0x180D)
- ✅ 使用Clevering自定义服务 (0xFFFA)
- ✅ 数据包格式遵循Clevering协议
- ✅ 设备名称过滤为 "Clevering"

**重新编译后应该就能连接了！** 🎉

---

*配置版本：V1.0*  
*适用设备：Clevering_EB6E*  
*协议版本：V102_20250310*


