@echo off
chcp 65001
title 泰拉瑞亚灾厄服务器
:StartServer
CALL :XBUTTON false
echo [%time%] 正在启动服务器...
echo [%time%] Starting Server...
".\jdk1.8.0_331\jre\bin\java.exe" -Xmx8g -Xms8g -jar paper-1.12.2-1620.jar nogui
echo [%time%] 若要关闭服务器，请在倒计时结束前关闭此窗口以免数据损坏。
echo [%time%] If you want to stop the server, close this tab before the countdown finishes to prevent data corruption.
CALL :XBUTTON true
TIMEOUT /T 10
goto StartServer

:: XBUTTON from https://github.com/illsk1lls/Win-11-Download-Prep-Tool/blob/main/Get-Win11.cmd
:: Referenced in: https://www.reddit.com/r/Batch/comments/154auie/how_to_prevent_a_batch_from_closing_i_mean_the/
:XBUTTON
>nul 2>&1 POWERSHELL -nop -c "(Add-Type -PassThru 'using System; using System.Runtime.InteropServices; namespace CloseButtonToggle { internal static class WinAPI { [DllImport(\"kernel32.dll\")] internal static extern IntPtr GetConsoleWindow(); [DllImport(\"user32.dll\")] [return: MarshalAs(UnmanagedType.Bool)] internal static extern bool DeleteMenu(IntPtr hMenu, uint uPosition, uint uFlags); [DllImport(\"user32.dll\")] [return: MarshalAs(UnmanagedType.Bool)] internal static extern bool DrawMenuBar(IntPtr hWnd); [DllImport(\"user32.dll\")] internal static extern IntPtr GetSystemMenu(IntPtr hWnd, [MarshalAs(UnmanagedType.Bool)]bool bRevert); const uint SC_CLOSE = 0xf060; const uint MF_BYCOMMAND = 0; internal static void ChangeCurrentState(bool state) { IntPtr hMenu = GetSystemMenu(GetConsoleWindow(), state); DeleteMenu(hMenu, SC_CLOSE, MF_BYCOMMAND); DrawMenuBar(GetConsoleWindow()); } } public static class Status { public static void Disable() { WinAPI.ChangeCurrentState(%1); } } }')[-1]::Disable()"
EXIT /b