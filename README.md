# 实验用NFC卡模拟

## 前置条件

安卓，设备已root，`/vendor/etc/libnfc-nxp.conf`可写，`/vendor/etc/libnfc-nci.conf`也需修改

## 功能

- 通过NFC读卡以添加新卡
- 手动添加新卡
- 模拟指定卡的Mifare UID

## TODO

未来有空了计划要写的功能，~但是得等我学会写Android应用~：

- 切换为现代UI设计风格（M3 Expressive?）
- 支持7/10字节UID
- 增加卡片选择的State持久化
- 微件/控制中心功能块？
- 自动先行读取系统NFC配置文件，解析得到模板，以更通用？
