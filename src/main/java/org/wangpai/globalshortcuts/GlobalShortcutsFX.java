package org.wangpai.globalshortcuts;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.wangpai.globalshortcuts.exception.GlobalShortcutsException;
import org.wangpai.globalshortcuts.exception.IncompatibleShortcutException;
import org.wangpai.globalshortcuts.exception.RepeatedShortcutException;
import org.wangpai.globalshortcuts.model.GlobalShortcutsLifecycle;
import org.wangpai.globalshortcuts.model.JIntellitypeShortcut;

import static org.wangpai.globalshortcuts.model.GlobalShortcutsLifecycle.AFTER_ALL_INSTANCES_EXIT;
import static org.wangpai.globalshortcuts.model.GlobalShortcutsLifecycle.AFTER_EXIT;
import static org.wangpai.globalshortcuts.model.GlobalShortcutsLifecycle.AFTER_SUSPEND;
import static org.wangpai.globalshortcuts.model.GlobalShortcutsLifecycle.BEFORE_RESUME;
import static org.wangpai.globalshortcuts.model.GlobalShortcutsLifecycle.BEFORE_START;

/**
 * JIntellitype 用法示例
 *
 * @since 2022-6-25
 */
@Accessors(chain = true)
public class GlobalShortcutsFX {
    private final GlobalShortcutsRegister register = new GlobalShortcutsRegister();
    private volatile boolean isRunning = false; // true 代表正在运行，false 代表暂停运行
    private volatile boolean isStarted = false;
    private volatile boolean needTerminate = false;
    private int suspendWaitedLoop = 1;
    private int runningLoop = 1;
    private Thread globalShortcutsThread = null; // 此线程很快就会自动结束
    private Thread customActivityThread = null;

    @Setter
    private Runnable mainActivityAction = null; // 用户单次任务

    /**
     * 允许快捷键重复
     *
     * @since 2022-9-30
     */
    @Setter
    private boolean repeatable = false;

    /**
     * 如果对不同生命周期设置了同一个快捷键，则当此快捷键按下时，视为同时触发了所有相同快捷键的生命周期。这有可能会有危险
     *
     * 不支持为同一生命周期设置多个快捷键
     *
     * @since 2022-9-30
     */
    public GlobalShortcutsFX setShortcut(GlobalShortcutsLifecycle lifecycle, JIntellitypeShortcut shortcut,
                                         Runnable action) throws GlobalShortcutsException {
        if (!this.repeatable && this.register.isShortcutExist(shortcut)) {
            throw new RepeatedShortcutException("异常：此快捷键已存在");
        }
        this.register.registerShortcut(lifecycle, shortcut, action);
        if (!this.register.checkTheLogicOfShortcuts()) {
            throw new IncompatibleShortcutException("异常：此快捷键重复且不兼容");
        }
        return this;
    }

    /**
     * 这是一个对外界暴露的函数，外界可以向其注入自定义方法以执行
     *
     * 注意：此方法会清除之前所有设置的生命周期方法
     *
     * @since 2022-7-27
     */
    public void easyExecute(Runnable function) {
        this.mainActivityAction = function;
        if (this.globalShortcutsThread == null) {
            this.globalShortcutsThread = new Thread(this::addGlobalShortcuts);
            this.globalShortcutsThread.setName("globalShortcutsThread" + GlobalShortcutsRegister.random.nextInt());

        }
        this.globalShortcutsThread.start(); // 开启子线程来运行
    }

    /**
     * @since 2022-7-27
     */
    public void execute() {
        if (this.globalShortcutsThread == null) {
            this.globalShortcutsThread = new Thread(this::addGlobalShortcuts);
            this.globalShortcutsThread.setName("globalShortcutsThread" + GlobalShortcutsRegister.random.nextInt());
        }
        this.globalShortcutsThread.start(); // 开启子线程来运行
    }

    /**
     * @since 2022-6-25
     * @lastModified 2022-9-30
     */
    private void run() {
        while (!this.needTerminate) {
            if (this.isRunning) {
                this.suspendWaitedLoop = 0;
                System.out.println(String.format("-----第 %d 圈任务开始执行------", this.runningLoop));
                try { // 此处必须使用 try 块吞掉所有可能的异常，否则本线程容易因注入代码抛出异常而无声中止
                    if (this.mainActivityAction != null) {
                        this.mainActivityAction.run();
                    }
                } catch (Throwable throwable) {
                    System.out.println(throwable);
                }
                System.out.println(String.format("-----第 %d 圈任务执行完毕------", this.runningLoop));
                ++this.runningLoop;
            } else {
                this.runningLoop = 1;
                if (this.suspendWaitedLoop == 0) {
                    System.out.println("-----任务暂停------");
                }
                ++this.suspendWaitedLoop;
                try {
                    Thread.interrupted(); // 在休眠前清除中断标志，否则有可能导致接下来的那次休眠失败
                    Thread.sleep(Integer.MAX_VALUE); // 休眠等待恢复运行
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
     * @lastModified 2022-9-30
     */
    private void addGlobalShortcuts() {
        this.customActivityThread = new Thread(this::run); // 开启子线程来运行
        this.customActivityThread.setName("customActivityThread" + GlobalShortcutsRegister.random.nextInt());
        HotkeyListener hotkeyListener = oneOfShortcutId -> {
            var lifecycles = this.register.getLifecycles(oneOfShortcutId);

            if (!this.isStarted && lifecycles != null && !lifecycles.contains(BEFORE_START)) {
                System.out.println("任务任务尚未开始");
                return;
            }
            if (!this.isStarted && lifecycles != null && lifecycles.contains(BEFORE_START)) {
                // 先执行用户注入的代码，然后才真正执行“开始”
                Runnable action = this.register.getAction(BEFORE_START);
                if (action != null) {
                    action.run();
                }
                System.out.println("任务开始");
                this.isStarted = true;
                this.isRunning = true;
                this.customActivityThread.start();
                return;
            }

            if (lifecycles.contains(BEFORE_RESUME)) {
                if (this.isRunning) {
                    System.out.println("任务正在运行，不需要恢复运行");
                } else {
                    // 先执行用户注入的代码，然后才真正执行“恢复运行”
                    var action = this.register.getAction(BEFORE_RESUME);
                    if (action != null) {
                        action.run();
                    }
                    System.out.println("任务恢复运行");
                    this.isRunning = true;
                    this.customActivityThread.interrupt();
                }
            }

            if (lifecycles.contains(AFTER_SUSPEND)) {
                if (this.isRunning) {
                    System.out.println("任务暂停");
                    this.isRunning = false;
                    this.customActivityThread.interrupt();
                    // 先暂停，然后执行用户注入的代码
                    var action = this.register.getAction(AFTER_SUSPEND);
                    if (action != null) {
                        action.run();
                    }
                } else {
                    System.out.println("任务已暂停，不需要再暂停");
                }
            }

            if (lifecycles.contains(AFTER_EXIT)) {
                this.isRunning = false;
                this.needTerminate = true;
                this.customActivityThread.interrupt();
                System.out.println("任务中止");

                // 先中止，然后执行用户注入的代码
                Runnable action = this.register.getAction(AFTER_EXIT);
                if (action != null) {
                    action.run();
                }

                this.register.removeRegisteredData();
            }

            if (lifecycles.contains(AFTER_ALL_INSTANCES_EXIT)) {
                this.isRunning = false;
                this.customActivityThread.interrupt();
                System.out.println("所有任务均中止");

                Runnable action = this.register.getAction(AFTER_ALL_INSTANCES_EXIT);
                if (action != null) {
                    action.run();
                }

                this.register.destroyAllData();
                System.exit(0); // 关闭主程序
            }
        };
        this.register.addHotKeyListener(hotkeyListener); // 添加监听
        System.out.println("正在监听快捷键...");
        // 运行本方法的 globalShortcutsThread 线程将在运行完本方法之后立刻就结束了
    }

    /**
     * 终止 customActivityThread 线程
     *
     * @since 2022-9-30
     */
    public GlobalShortcutsFX terminateCustomThread() {
        this.isRunning = false;
        this.needTerminate = true;
        this.customActivityThread.interrupt();
        return this;
    }

    /**
     * @since 2022-9-30
     */
    private class GlobalShortcutsRegister {
        public static final Random random = new Random();
        private static final List<GlobalShortcutsRegister> globalRegisters = new CopyOnWriteArrayList<>();
        private final JIntellitype jintellitype = JIntellitype.getInstance();
        private HotkeyListener hotkeyListener = null;

        /**
         * 考虑到允许用户创建本类的多个实例，为了在多个实例下，本类的快捷键标识符不重复，所以设置此字段
         *
         * 使用 DualHashBidiMap 是为了支持通过 id 来反向搜索到 lifecycle。
         * DualHashBidiMap 只能用于 1 对 1 映射，否则只要 key 或 value 存在相同，就会发生相应的覆盖
         *
         * @since 2022-9-30
         */
        private final DualHashBidiMap<GlobalShortcutsLifecycle, Integer> idOfLifecycles = new DualHashBidiMap<>();

        /**
         * 储存每个生命周期对应的快捷键
         *
         * @since 2022-9-30
         */
        private final Map<GlobalShortcutsLifecycle, JIntellitypeShortcut> lifecycleBindShortcut =
                new ConcurrentHashMap<>();

        /**
         * 储存同一快捷键对应的生命周期
         *
         * @since 2022-9-30
         */
        private final Map<JIntellitypeShortcut, Set<GlobalShortcutsLifecycle>> shortcutBindLifecycle =
                new ConcurrentHashMap<>();

        /**
         * 储存每个生命周期对应的要触发的行为
         *
         * @since 2022-9-30
         */
        private final Map<GlobalShortcutsLifecycle, Runnable> lifecycleBindAction =
                new ConcurrentHashMap<>();

        /**
         * @since 2022-9-30
         */
        public GlobalShortcutsRegister() {
            for (var lifecycle : GlobalShortcutsLifecycle.values()) {
                int code = random.nextInt();
                this.idOfLifecycles.put(lifecycle, code);
            }
            globalRegisters.add(this);
        }

        /**
         * @since 2022-9-30
         */
        public void addHotKeyListener(HotkeyListener listener) {
            this.hotkeyListener = listener;
            this.jintellitype.addHotKeyListener(this.hotkeyListener); // 添加监听
        }

        /**
         * 收集有相同快捷键的 lifecycle
         *
         * @since 2022-9-30
         */
        private void collectShortcuts() {
            for (var pair : this.lifecycleBindShortcut.entrySet()) {
                var lifecycle = pair.getKey();
                var shortcut = pair.getValue();
                if (this.shortcutBindLifecycle.containsKey(shortcut)) {
                    var lifecycles = this.shortcutBindLifecycle.get(shortcut);
                    lifecycles.add(lifecycle);
                } else {
                    var lifecycles = new HashSet<GlobalShortcutsLifecycle>();
                    lifecycles.add(lifecycle);
                    this.shortcutBindLifecycle.put(shortcut, lifecycles);
                }
            }
        }

        /**
         * @since 2022-9-30
         */
        public void registerShortcut(GlobalShortcutsLifecycle lifecycle,
                                     JIntellitypeShortcut shortcut, Runnable action) {
            var id = this.idOfLifecycles.get(lifecycle);
            this.jintellitype.registerHotKey(id, shortcut.getModifier(), shortcut.getKeycode());
            this.lifecycleBindShortcut.put(lifecycle, shortcut);
            this.lifecycleBindAction.put(lifecycle, action);
            this.collectShortcuts();
        }

        /**
         * 根据 lifecycle 的 id，查找与之有相同快捷键的 lifecycle
         *
         * @since 2022-9-30
         */
        public Set<GlobalShortcutsLifecycle> getLifecycles(int id) {
            // 根据 id 查找对应的 lifecycle
            var oneOfLifecycle = this.idOfLifecycles.getKey(id);
            var shortcut = this.lifecycleBindShortcut.get(oneOfLifecycle);
            return this.shortcutBindLifecycle.get(shortcut);

            // 使用如下注释代码也可以
//            // 根据 lifecycle 查找对应的快捷键
//            var shortcut = this.lifecycleBindShortcut.get(oneOfLifecycle);
//            var result = new HashSet<GlobalShortcutsLifecycle>();
//            // 收集有相同快捷键的 lifecycle
//            for (var pair : this.lifecycleBindShortcut.entrySet()) {
//                if (pair.getValue().equals(shortcut)) {
//                    result.add(pair.getKey());
//                }
//            }
//            return result;
        }

        /**
         * @since 2022-9-30
         */
        public JIntellitypeShortcut getShortcut(GlobalShortcutsLifecycle lifecycle) {
            return this.lifecycleBindShortcut.get(lifecycle);
        }

        /**
         * @since 2022-9-30
         */
        public Runnable getAction(GlobalShortcutsLifecycle lifecycle) {
            return this.lifecycleBindAction.get(lifecycle);
        }


        /**
         * @return true 表示检查通过（无错误）
         * @since 2022-9-30
         */
        public boolean checkTheLogicOfShortcuts() {
            for (var lifecycles : this.shortcutBindLifecycle.values()) {
                if (lifecycles.size() == 1) {
                    continue;
                }
                // 如果是将“开始”、“恢复运行”快捷键合并
                if (lifecycles.size() == 2
                        && lifecycles.contains(BEFORE_START)
                        && lifecycles.contains(BEFORE_RESUME)) {
                    continue; // continue 表示合法
                }

                return false; // 只要有上述规定外的其它行为，即视为非法
            }
            return true; // 如果前面没有检测出问题，则视为合法
        }

        /**
         * 判断此快捷键是否已经存在
         *
         * @since 2022-9-30
         */
        public boolean isShortcutExist(JIntellitypeShortcut shortcut) {
            var lifecycles = this.shortcutBindLifecycle.get(shortcut);
            if (lifecycles == null || lifecycles.size() == 0) {
                return false;
            } else {
                return true;
            }

            // 使用如下注释代码也可以
//            for (var pair : this.lifecycleBindShortcut.entrySet()) {
//                if (pair.getValue().equals(shortcut)) {
//                    return true;
//                }
//            }
//            return false;
        }

        /**
         * 此方法不会销毁全局 JIntellitype 线程，只会清除本实例注册过的快捷键
         *
         * @since 2022-9-30
         */
        public void removeRegisteredData() {
            this.jintellitype.removeHotKeyListener(this.hotkeyListener);
            for (var lifecycleId : this.idOfLifecycles.values()) {
                this.jintellitype.unregisterHotKey(lifecycleId);
            }
        }

        /**
         * @since 2022-9-30
         */
        public void callOuterDestroy() {
            GlobalShortcutsFX.this.terminateCustomThread();
        }

        /**
         * 销毁所有 GlobalShortcutsFx 实例的数据
         *
         * @since 2022-9-30
         */
        public void destroyAllData() {
            for (var register : globalRegisters) {
                register.removeRegisteredData();
                register.callOuterDestroy();
            }
            this.jintellitype.cleanUp();
        }
    }
}
