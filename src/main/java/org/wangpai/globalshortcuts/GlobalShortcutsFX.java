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
@Accessors(chain = true)
public class GlobalShortcutsFX {
    private static final int FIRST_SHORTCUT = 1; // 模拟任务开始快捷键
    private static final int SECOND_SHORTCUT = 2; // 模拟手动暂停任务快捷键
    private static final int THIRD_SHORTCUT = 3; // 模拟手动结束任务快捷键
    private final State state = new State();
    private boolean started = false;
    private int suspendWaitedLoop = 1;
    private int runningLoop = 1;
    private HotkeyListener hotkeyListener = null;
    private Thread globalShortcutsThread = null;
    private Thread mainTaskThread;

    @Setter
    private Runnable customActivity = null; // 用户单次任务
    @Setter
    private Runnable customSuspend = null; // 暂停用户任务时会触发的方法
    @Setter
    private Runnable customResume = null; // 解除暂停状态，继续执行用户任务时会触发的方法
    @Setter
    private Runnable customExit = null; // 正常终止用户任务时会触发的方法。如果本程序因外部强行终止，则此方法有可能无法执行

    /**
     * 这是一个对外界暴露的函数，外界可以向其注入自定义方法以执行
     *
     * 注意：此方法会清除之前所有设置的生命周期方法
     *
     * @since 2022-7-27
     */
    public void easyExecute(Runnable function) {
        this.customActivity = function;
        this.customSuspend = null;
        this.customResume = null;
        this.customExit = null;
        if (this.globalShortcutsThread == null) {
            this.globalShortcutsThread = new Thread(this::addGlobalShortcuts);
        }
        this.globalShortcutsThread.start(); // 开启子线程来运行
    }

    /**
     * @since 2022-7-27
     */
    public void execute() {
        if (this.globalShortcutsThread == null) {
            this.globalShortcutsThread = new Thread(this::addGlobalShortcuts);
        }
        this.globalShortcutsThread.start(); // 开启子线程来运行
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-7-27
     */
    private void run() {
        while (true) {
            if (this.state.isRunning()) {
                this.suspendWaitedLoop = 0;
                System.out.println(String.format("-----第 %d 圈任务开始执行------", this.runningLoop++));
                try { // 此处必须使用 try 块吞掉所有可能的异常，否则本线程容易因注入代码抛出异常而无声中止
                    if (this.customActivity != null) {
                        this.customActivity.run();
                    }
                } catch (Throwable throwable) {
                    System.out.println(throwable);
                }
                System.out.println(String.format("-----第 %d 圈任务执行完毕------", this.runningLoop));
            } else {
                this.runningLoop = 1;
                if (this.suspendWaitedLoop == 0) {
                    System.out.println("-----任务暂停------");
                    if (this.customSuspend != null) {
                        this.customSuspend.run();
                    }
                }
                ++this.suspendWaitedLoop;
                try {
                    Thread.interrupted(); // 在休眠前清除中断标志，否则有可能导致接下来的那次休眠失败
                    Thread.sleep(Integer.MAX_VALUE); // 休眠等待恢复运行
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                    this.suspendWaitedLoop = 0;
                    System.out.println("-----任务恢复运行-----");
                    if (this.customResume != null) {
                        this.customResume.run();
                    }
                }
            }
        }
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-7-27
     */
    private void addGlobalShortcuts() {
        JIntellitype.getInstance().registerHotKey(FIRST_SHORTCUT,
                JIntellitype.MOD_SHIFT + JIntellitype.MOD_ALT, 'K'); // SHIFT + ALT + K
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
                            System.out.println("快捷键 SHIFT + ALT + K 被触发：任务正在运行，不需要再开始");
                        } else {
                            System.out.println("快捷键 SHIFT + ALT + K 被触发，任务恢复运行");
                            synchronized (this.state) { // 此处必须上锁
                                this.state.setRunning(true);
                                this.mainTaskThread.interrupt();
                            }
                        }
                    } else {
                        System.out.println("快捷键 SHIFT + ALT + K 被触发，任务开始");
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
                    if (this.customExit != null) {
                        this.customExit.run();
                    }
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
