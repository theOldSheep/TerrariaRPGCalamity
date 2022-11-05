@echo off
title 泰拉瑞亚RPG服务器
:StartServer
"C:\Program Files\Java\jdk1.8.0_331\jre\bin\java.exe" -Xmx8g -Xms8g -jar -jar paper-1.11.2.jar nogui
PAUSE
echo (%time%) Server closed/crashed... restarting!
goto StartServer