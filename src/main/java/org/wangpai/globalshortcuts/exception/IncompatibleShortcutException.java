package org.wangpai.globalshortcuts.exception;

/**
 * 在设置了重复快捷键时，如果这些重复的行为在逻辑上本来就不能同时发生，将引发本异常
 *
 * @since 2022-9-30
 */
public class IncompatibleShortcutException extends GlobalShortcutsException {
    public IncompatibleShortcutException() {
        super("异常：不兼容的快捷键");
    }

    public IncompatibleShortcutException(String msg) {
        super(msg);
    }

    public IncompatibleShortcutException(String msg, Object obj) {
        super(msg, obj);
    }
}
