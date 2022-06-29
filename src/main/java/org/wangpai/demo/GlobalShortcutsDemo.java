package org.wangpai.demo;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * JIntellitype 用法示例
 *
 * @since 2022-6-25
 */
public class GlobalShortcutsDemo {
    private static final int FIRST_SHORTCUT = 1; // 模拟任务开始快捷键
    private static final int SECOND_SHORTCUT = 2; // 模拟手动结束任务快捷键
    private static final int THIRD_SHORTCUT = 3; // 模拟手动结束任务快捷键
    private static final State state = new State();
    private static boolean started = false;
    private static int suspendWaitedLoop = 1;
    private static int runningLoop = 1;
    private static HotkeyListener hotkeyListener = null;

    public static void main(String[] args) {
        new Thread(GlobalShortcutsDemo::addGlobalShortcuts).start(); // 开启子线程来运行
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-6-29
     */
    private static void run() {
        while (true) {
            if (state.isRunning()) {
                try {
                    suspendWaitedLoop = 0;
                    System.out.println(String.format("-----第 %d 圈任务开始执行------", runningLoop++));
                    Thread.sleep(10000); // 模拟耗时任务（10 秒）
                    System.out.println(String.format("-----第 %d 圈任务执行完毕------", runningLoop));
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                    runningLoop = 1;
                    System.out.println("-----本圈任务未完成就被打断------");
                }
            } else {
                runningLoop = 1;
                if (suspendWaitedLoop == 0) {
                    System.out.println("-----任务暂停------");
                }
                ++suspendWaitedLoop;
                try {
                    Thread.interrupted(); // 在休眠前清除中断标志，否则有可能导致接下来的那次休眠失败
                    Thread.sleep(10000); // 休眠等待恢复运行
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                    suspendWaitedLoop = 0;
                    System.out.println("-----任务恢复运行-----");
                }
            }
        }
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-6-29
     */
    private static void addGlobalShortcuts() {
        JIntellitype.getInstance().registerHotKey(FIRST_SHORTCUT, 0, 'K'); // K
        JIntellitype.getInstance().registerHotKey(SECOND_SHORTCUT,
                JIntellitype.MOD_SHIFT + JIntellitype.MOD_ALT, 'L'); // SHIFT + ALT + L
        JIntellitype.getInstance().registerHotKey(THIRD_SHORTCUT,
                JIntellitype.MOD_SHIFT + JIntellitype.MOD_ALT, 'J'); // SHIFT + ALT + J
        final var mainTaskThread = new Thread(GlobalShortcutsDemo::run); // 开启子线程来运行
        hotkeyListener = code -> {
            switch (code) {
                case FIRST_SHORTCUT -> {
                    if (started) {
                        if (state.isRunning()) {
                            System.out.println("快捷键 K 被触发：任务正在运行，不需要再开始");
                        } else {
                            System.out.println("快捷键 K 被触发，任务恢复运行");
                            synchronized (state) { // 此处必须上锁
                                state.setRunning(true);
                                mainTaskThread.interrupt();
                            }
                        }
                    } else {
                        System.out.println("快捷键 K 被触发，任务开始");
                        synchronized (state) { // 此处必须上锁
                            state.setRunning(true);
                            mainTaskThread.start();
                        }
                        started = true;
                    }
                }
                case SECOND_SHORTCUT -> {
                    if (state.isRunning()) {
                        System.out.println("快捷键 SHIFT + ALT + L 被触发，任务暂停");
                        synchronized (state) { // 此处必须上锁
                            state.setRunning(false);
                            mainTaskThread.interrupt();
                        }
                    } else {
                        System.out.println("快捷键 SHIFT + ALT + L 被触发：任务已暂停，不需要再暂停");
                    }
                }
                case THIRD_SHORTCUT -> {
                    System.out.println("快捷键 SHIFT + ALT + J 被触发，任务中止");
                    JIntellitype.getInstance().removeHotKeyListener(hotkeyListener); // 移除快捷键触发后的动作
                    JIntellitype.getInstance().unregisterHotKey(FIRST_SHORTCUT); // 移除快捷键 FIRST_SHORTCUT
                    JIntellitype.getInstance().unregisterHotKey(SECOND_SHORTCUT); // 移除快捷键 SECOND_SHORTCUT
                    JIntellitype.getInstance().unregisterHotKey(THIRD_SHORTCUT); // 移除快捷键 THIRD_SHORTCUT
                    mainTaskThread.interrupt();
                    System.exit(0); // 关闭主程序
                }
                default -> System.out.println("监听了此快捷键但是未定义其行为");
            }
        };
        JIntellitype.getInstance().addHotKeyListener(hotkeyListener); // 添加监听
        System.out.println("正在监听快捷键...");
    }

    @Getter
    @Setter
    @ToString
    @Accessors(chain = true)
    static class State {
        private boolean running = false; // true 代表正在运行，false 代表暂停运行
    }
}
