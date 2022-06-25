package org.wangpai.demo;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;
import org.wangpai.commonutil.multithreading.CentralDatabase;
import org.wangpai.commonutil.multithreading.Multithreading;

/**
 * JIntellitype 用法示例
 *
 * @since 2022-6-25
 */
public class GlobalShortcutsDemo {
    public static final int FIRST_SHORTCUT = 1; // 模拟任务开始快捷键
    public static final int SECOND_SHORTCUT = 2; // 模拟手动结束任务快捷键
    private static HotkeyListener hotkeyListener = null;

    public static void main(String[] args) {
        addGlobalShortcuts();
    }

    /**
     * @since 2022-6-25
     */
    private static void run() {
        try {
            Thread.sleep(10000); // 模拟耗时任务（10 秒）
        } catch (InterruptedException exception) {
            System.out.println("任务运行在完成前被中止！");
            exception.printStackTrace();
        }
    }

    /**
     * @since 2022-6-25
     */
    private static void addGlobalShortcuts() {
        JIntellitype.getInstance().registerHotKey(FIRST_SHORTCUT, 0, 'K'); // K
        JIntellitype.getInstance().registerHotKey(SECOND_SHORTCUT,
                JIntellitype.MOD_SHIFT + JIntellitype.MOD_ALT, 'L'); // SHIFT + ALT + L
        hotkeyListener = code -> {
            switch (code) {
                case FIRST_SHORTCUT -> {
                    System.out.println("快捷键 K 被触发，任务开始");
                    Multithreading.execute(GlobalShortcutsDemo::run);
                }
                case SECOND_SHORTCUT -> {
                    System.out.println("快捷键 SHIFT + ALT + L 被触发，任务结束");
                    JIntellitype.getInstance().removeHotKeyListener(hotkeyListener); // 移除快捷键触发后的动作
                    JIntellitype.getInstance().unregisterHotKey(FIRST_SHORTCUT); // 移除快捷键 FIRST_SHORTCUT
                    JIntellitype.getInstance().unregisterHotKey(SECOND_SHORTCUT); // 移除快捷键 SECOND_SHORTCUT
                    CentralDatabase.multithreadingClosed(); // 中断子线程，回收子线程资源
                    System.exit(0); // 关闭主程序
                }
                default -> System.out.println("监听了此快捷键但是未定义其行为");
            }
        };
        JIntellitype.getInstance().addHotKeyListener(hotkeyListener); // 添加监听
        System.out.println("正在监听快捷键...");
    }
}
