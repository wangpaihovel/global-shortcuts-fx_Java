package org.wangpai.globalshortcuts;

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
public class GlobalShortcutsFX {
    private static final int FIRST_SHORTCUT = 1; // 模拟任务开始快捷键
    private static final int SECOND_SHORTCUT = 2; // 模拟手动结束任务快捷键
    private static final int THIRD_SHORTCUT = 3; // 模拟手动结束任务快捷键
    private final State state = new State();
    private boolean started = false;
    private int suspendWaitedLoop = 1;
    private int runningLoop = 1;
    private HotkeyListener hotkeyListener = null;
    private Thread globalShortcutsThread;
    private Thread mainTaskThread;
    private Runnable customActivity;

    /**
     * 这是一个对外界暴露的函数，外界可以向其注入自定义方法以执行
     *
     * @since 2022-6-30
     */
    public void execute(Runnable function) {
        this.customActivity = function;
        this.globalShortcutsThread = new Thread(this::addGlobalShortcuts);
        this.globalShortcutsThread.start(); // 开启子线程来运行
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-6-29
     */
    private void run() {
        while (true) {
            if (this.state.isRunning()) {
                this.suspendWaitedLoop = 0;
                System.out.println(String.format("-----第 %d 圈任务开始执行------", this.runningLoop++));
                try { // 此处必须使用 try 块吞掉所有可能的异常，否则本线程容易因注入代码抛出异常而无声中止
                    this.customActivity.run();
                } catch (Throwable throwable) {
                    System.out.println(throwable);
                }
                System.out.println(String.format("-----第 %d 圈任务执行完毕------", this.runningLoop));
            } else {
                this.runningLoop = 1;
                if (this.suspendWaitedLoop == 0) {
                    System.out.println("-----任务暂停------");
                }
                ++this.suspendWaitedLoop;
                try {
                    Thread.interrupted(); // 在休眠前清除中断标志，否则有可能导致接下来的那次休眠失败
                    Thread.sleep(10000); // 休眠等待恢复运行
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                    this.suspendWaitedLoop = 0;
                    System.out.println("-----任务恢复运行-----");
                }
            }
        }
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-6-29
     */
    private void addGlobalShortcuts() {
        JIntellitype.getInstance().registerHotKey(FIRST_SHORTCUT, 0, 'K'); // K
        JIntellitype.getInstance().registerHotKey(SECOND_SHORTCUT,
                JIntellitype.MOD_SHIFT + JIntellitype.MOD_ALT, 'L'); // SHIFT + ALT + L
        JIntellitype.getInstance().registerHotKey(THIRD_SHORTCUT,
                JIntellitype.MOD_SHIFT + JIntellitype.MOD_ALT, 'J'); // SHIFT + ALT + J
        this.mainTaskThread = new Thread(this::run); // 开启子线程来运行
        this.hotkeyListener = code -> {
            switch (code) {
                case FIRST_SHORTCUT -> {
                    if (this.started) {
                        if (this.state.isRunning()) {
                            System.out.println("快捷键 K 被触发：任务正在运行，不需要再开始");
                        } else {
                            System.out.println("快捷键 K 被触发，任务恢复运行");
                            synchronized (this.state) { // 此处必须上锁
                                this.state.setRunning(true);
                                this.mainTaskThread.interrupt();
                            }
                        }
                    } else {
                        System.out.println("快捷键 K 被触发，任务开始");
                        synchronized (this.state) { // 此处必须上锁
                            this.state.setRunning(true);
                            this.mainTaskThread.start();
                        }
                        this.started = true;
                    }
                }
                case SECOND_SHORTCUT -> {
                    if (this.state.isRunning()) {
                        System.out.println("快捷键 SHIFT + ALT + L 被触发，任务暂停");
                        synchronized (this.state) { // 此处必须上锁
                            this.state.setRunning(false);
                            this.mainTaskThread.interrupt();
                        }
                    } else {
                        System.out.println("快捷键 SHIFT + ALT + L 被触发：任务已暂停，不需要再暂停");
                    }
                }
                case THIRD_SHORTCUT -> {
                    System.out.println("快捷键 SHIFT + ALT + J 被触发，任务中止");
                    JIntellitype.getInstance().removeHotKeyListener(this.hotkeyListener); // 移除快捷键触发后的动作
                    JIntellitype.getInstance().unregisterHotKey(FIRST_SHORTCUT); // 移除快捷键 FIRST_SHORTCUT
                    JIntellitype.getInstance().unregisterHotKey(SECOND_SHORTCUT); // 移除快捷键 SECOND_SHORTCUT
                    JIntellitype.getInstance().unregisterHotKey(THIRD_SHORTCUT); // 移除快捷键 THIRD_SHORTCUT
                    this.mainTaskThread.interrupt();
                    System.exit(0); // 关闭主程序
                }
                default -> System.out.println("监听了此快捷键但是未定义其行为");
            }
        };
        JIntellitype.getInstance().addHotKeyListener(this.hotkeyListener); // 添加监听
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
