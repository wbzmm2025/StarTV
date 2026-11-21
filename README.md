# StarTV

Android TV 直播电视应用 + 每日自动更新的电视源数据。

- **后台（GitHub Actions）**：每天 UTC+8 00:00（UTC 16:00）自动从多个开源维护的 IPTV 源抓取
  **CCTV 全套频道 + 37 个省级卫视**，校验可用性后以固定 JSON 格式写入本仓库根目录的
  [startv.json](startv.json)。
- **客户端（android/）**：Android TV 应用，当天首次打开时通过镜像加速下载 `startv.json`
  到本地（当天已下载过则不重复下载），解析后按「央视 / 卫视」分组展示，遥控器选台播放，
  单条线路失败自动切换下一条，全部失败时明确提示用户。

## startv.json 格式

```json
{
  "update_time": "2026-11-06",
  "version": 1,
  "channels": [
    {
      "name": "CCTV-1 综合",
      "group": "央视",
      "logo": "http://example.com/logo/cctv1.png",
      "urls": ["http://xxx/x.m3u8", "http://yyy/y.m3u8"]
    }
  ]
}
```

| 字段                  | 类型   | 必填 | 说明                                            |
| --------------------- | ------ | ---- | ----------------------------------------------- |
| `update_time`         | string | 是   | 源更新时间（北京时间 yyyy-MM-dd）               |
| `version`             | int    | 否   | 格式版本号，以后改结构时方便兼容               |
| `channels`            | array  | 是   | 频道列表                                        |
| `channels[].name`     | string | 是   | 频道名，显示在界面上                            |
| `channels[].group`    | string | 是   | 分组，如「央视」「卫视」                        |
| `channels[].logo`     | string | 否   | 频道图标 URL，没有就留空                        |
| `channels[].urls`     | array  | 是   | 多个播放地址，按顺序尝试，第一个失败自动切下一个 |

## 使用步骤（仓库主人手动操作）

### 1. 创建仓库并上传

1. 登录 GitHub 账号 **wbzmm2025**，新建**公开**仓库，名字 `StarTV`（不要勾选任何初始化文件）。
2. 在本目录执行：

```bash
cd StarTV
git remote add origin https://github.com/wbzmm2025/StarTV.git
git branch -M main
git push -u origin main
```

（首次 push 会要求登录，用浏览器授权或 PAT 即可。）

### 2. 启用每日更新

- 进入仓库 `Settings -> Actions -> General`，**Workflow permissions** 选择
  `Read and write permissions`，保存。这样 Actions 才能提交 `startv.json`。
- 手动触发一次：仓库 `Actions` 页 → `Update StarTV Sources` → `Run workflow`。
  运行成功后根目录会出现真实的 `startv.json`。
- 之后每天北京时间 00:00 自动更新。

### 3. 获取 APK 安装包

- **push 到 main 后 Actions 会自动打包**：进入仓库 `Actions` 页 → 找到最新一次
  `Build APK` 运行 → 在该次运行页面底部的 `Artifacts` 下载 `StarTV-debug-apk`
  → 传到电视/盒子安装即可（也可以在 `Build APK` 页面手动 `Run workflow`）。
- 本地打包（可选）：`cd android && gradle assembleDebug`。

## 内置离线兜底源

APK 内置了一份 `android/app/src/main/assets/startv_default.json`（CCTV 主力频道 +
主要省级卫视，使用北京邮电大学 ivi.bupt.edu.cn 等教育网公开流）。当 GitHub 及所有镜像
都不可用、且设备上没有任何本地缓存时，应用会自动改用内置源播放，并在顶栏标注
「(内置源)」，保证 App 永远可以打开。内置源只是兜底，频道数量和时效不如每日更新的
`startv.json`。

## 客户端遥控器操作

| 界面       | 按键                                       | 功能                     |
| ---------- | ------------------------------------------ | ------------------------ |
| 频道列表页 | 方向键                                     | 移动焦点                 |
|            | OK 键                                      | 播放当前频道             |
| 播放页     | 上 / 下                                    | 上一个 / 下一个频道      |
|            | OK 键                                      | 呼出左侧频道列表浮层     |
|            | 返回键                                     | 关闭浮层 / 退出播放      |

## 目录结构

```
StarTV/
├── .github/workflows/
│   ├── update.yml          # 每日爬源工作流
│   └── build.yml           # APK 构建工作流
├── scripts/
│   ├── fetch_sources.py    # 抓取/合并/校验脚本
│   └── requirements.txt
├── startv.json             # 每日更新的电视源数据
└── android/                # Android TV 客户端 (Kotlin + Media3/ExoPlayer)
```

## 数据来源（开源 IPTV 项目）

- [fanmingming/live](https://github.com/fanmingming/live)
- [vbskycn/iptv](https://github.com/vbskycn/iptv)
- [iptv-org/iptv](https://github.com/iptv-org/iptv)
- [Guovin/iptv-api](https://github.com/Guovin/iptv-api)
- [suxuang/myIPTV](https://github.com/suxuang/myIPTV)
- [YueChan/Live](https://github.com/YueChan/Live)

仅供学习交流，直播源版权归各电视台所有。
