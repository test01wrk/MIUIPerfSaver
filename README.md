# MIUI 性能救星
> 移除 MIUI 对应用的性能限制，以最高帧率运行应用

## 介绍
本模块旨在用各种方式提升系统运行应用的性能。
目前支持的功能：
- 对指定应用解除 MIUI 系统 “电量与性能” 应用云控限制屏幕刷新率
  > “电量与性能” 应用会根据前台应用情况，限制应用的运行帧数。比如王者荣耀等游戏会被限制 60 刷新率，导致游戏最高只能达到 60 帧


## 使用方式
1. 安装 MIUIPerfSaver.apk [MIUI 性能救星]
2. 在 LSPosed 管理器中启用模块，首次启用模块、更新模块版本时，需要停止"性能与电量"应用一次
3. 在 MIUI 性能救星 应用自己的设置中，勾选需要解除刷新率限制的应用。这里的改动立即生效，重启也仍然生效，无需强制停止任何应用


## 反馈 bug
请在 https://github.com/test01wrk/MIUIPerfSaver/issues 创建 issue，并提供 LSPosed 管理器中的详细日志

## 感谢
应用列表界面的 UI 和基础逻辑，改自 [XAppDebug](https://github.com/Palatis/XAppDebug) 项目

---

# MIUI performance saver
> Remove MIUI\'s performance limit, run app at maximum FPS

UI from [XAppDebug](https://github.com/Palatis/XAppDebug), thanks~

