@echo off
chcp 65001
title 泰拉瑞亚灾厄服务器
:StartServer
echo [%time%] 正在启动服务器...
echo [%time%] Starting Server...
"C:\Program Files\Java\jdk1.8.0_331\jre\bin\java.exe" -Xmx8g -Xms8g -jar paper-1.12.2-1620.jar nogui
echo [%time%] 若要关闭服务器，请在倒计时结束前关闭此窗口以免数据损坏。
echo [%time%] If you want to stop the server, close this tab before the countdown finishes to prevent data corruption.
TIMEOUT /T 10
goto StartServer