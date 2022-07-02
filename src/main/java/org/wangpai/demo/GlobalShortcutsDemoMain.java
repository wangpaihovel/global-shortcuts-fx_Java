package org.wangpai.demo;

import org.wangpai.globalshortcuts.GlobalShortcutsFX;

public class GlobalShortcutsDemoMain {
    private static void customActivity() {
        try {
            Thread.sleep(10000); // 模拟耗时任务（10 秒）
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new GlobalShortcutsFX()
                .setCustomActivity(GlobalShortcutsDemoMain::customActivity)
                .setCustomResume(() -> System.out.println("######## customActivity 恢复执行 ########"))
                .setCustomSuspend(() -> System.out.println("######## customActivity 暂停执行 ########"))
                .setCustomExit(() -> System.out.println("######## customActivity 终止执行 ########"))
                .execute();
    }
}
