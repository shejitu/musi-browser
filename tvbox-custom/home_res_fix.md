# 闪退修复 v1.3
根因: 启动入口改为 MusiHomeActivity 后跳过了 HomeActivity.onCreate,
而 ApiConfig.loadConfig() 调用 HomeActivity.getRes() 时静态 res 仍为 null → NPE 开机闪退。
修复: HomeActivity.getRes() 加 null 回退 App.getInstance().getResources()。
(已直接改在源码, 此文件仅作记录)
